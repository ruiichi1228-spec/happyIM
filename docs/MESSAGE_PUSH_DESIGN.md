# 消息推送模块设计

1. 基础设施  ← 先搭好
   ├─ pom.xml 加 MongoDB + RabbitMQ 依赖
   ├─ MongoDB 消息集合 + 索引
   ├─ RabbitMQ 配置（Exchange + 队列绑定）
   └─ 80-bit 消息 ID 生成器

2. 发送消息  ← 核心通路
   ├─ POST /api/conversations/{id}/messages（存 MongoDB + 投递 MQ + 返回 ack）
   └─ 消息过滤链（AC 自动机）

3. 拉取消息  ← 前端需要
   ├─ GET /api/conversations/{id}/messages（游标分页）
   └─ GET /api/conversations（懒加载会话摘要）

4. WebSocket 服务
   ├─ 连接管理 + 心跳 + 在线状态（Redis TTL）
   ├─ 会话绑定（enter/leave conversation）
   └─ MQ Consumer 消费 → 写 Feed → 推送

5. 前端 Chat 页面
   ├─ 消息列表渲染 + 发送 + 游标滚动
   └─ WebSocket 连接 + 实时接收


## 1. 架构概览

```
┌──────────┐   HTTP POST    ┌──────────────┐   publish    ┌──────────┐
│  前端 A   │ ────────────→ │  MessageService│ ──────────→ │ RabbitMQ │
└──────────┘   /messages    │  - 校验        │             │ Exchange │
                            │  - 生成ID      │             └────┬─────┘
                            │  - 存MongoDB   │                  │
                            │  - 投递MQ      │           consume│
                            │  - 返回ack     │                  │
                            └──────────────┘    ┌──────────────┴──────────┐
                                                 │  Consumer-1  Consumer-2 │
                                                 │  消费 + 分发 + 写Redis   │
                                                 └─────────────────────────┘
                                                             │
                                                             ▼
                            ┌──────────────────────────────────────────┐
                            │           用户 Feed (Redis)               │
                            │  feed:{uid}:{convId}  ZSET               │
                            │  read_cursor  +  unread_count            │
                            └──────────────────────────────────────────┘
```

---

## 2. 消息 ID

### 80-bit 结构（融云风格）

```
┌──────────────┬──────────┬───────┬──────────────┐
│ 时间戳(42Bit)│ 自旋ID   │ 会话类│ 会话ID(22Bit)│
│              │(12Bit)   │ 型    │              │
│              │          │(4Bit) │              │
└──────────────┴──────────┴───────┴──────────────┘

编码: 32进制 (去掉了0,1,O,I), 16字符, 每4字符用 - 分隔
示例: BD8U-FCOJ-LDC5-L789
```

### Redis 每会话独立序列生成器

```
Key:  msgid:spin:{conversationId}
操作: INCR msgid:spin:p_10001_10002  → 返回 1, 2, 3...
TTL:  7天

同毫秒内同一会话发多条消息时，自旋ID递增（12bit, 0-4095）
不同会话之间互不干扰，无热点 Key
```

### 生成方法

```java
public String generateMessageId(String conversationId, int conversationType) {
    // 1. 时间戳 42bit
    long timestamp = System.currentTimeMillis();

    // 2. Redis INCR 获取当前会话的序列号 (自旋ID 12bit)
    long spin = redis.incr("msgid:spin:" + conversationId) & 0xFFF;

    // 3. 会话类型 4bit (0=私聊, 1=群聊)
    long typeBits = conversationType & 0xF;

    // 4. 会话ID哈希 22bit
    long convHash = hash(conversationId) & 0x3FFFFF;

    // 5. 拼接 80bit → 32进制编码 → 16字符 + 3个分隔符
    // 返回: BD8U-FCOJ-LDC5-L789
}
```

### 为什么不用 #{convId}#{seq}

| | #{convId}#{seq} | 80-bit 融云风格 |
|------|------|------|
| B+树有序 | 字符串比较，前缀变长 | 时间戳在高位，天然有序 |
| 存储大小 | 变长字符串 | 固定 19 字符 |
| 分库分表 | 需额外维护映射 | 会话ID编码在低22bit，直接定位 |
| 去重能力 | 需额外检查 | message_id 天然唯一 |

---

## 3. MongoDB 消息文档

```json
{
  "messageId": "BD8U-FCOJ-LDC5-L789",
  "conversationId": "g_10000",
  "conversationType": 1,
  "fromUserId": 10001,
  "content": "大家好",
  "messageType": "text",
  "createdAt": 1716123456789
}
```

索引：
```
{ messageId: 1 } unique
{ conversationId: 1, createdAt: 1 }
```

---

## 4. RabbitMQ 设计

### 核心思路：查 Redis 路由表，投递到目标 WS 实例

每个 WS 实例启动时创建自己的队列，用户上线时在 Redis 记录所在实例。

```
Exchange:  chat.exchange  (topic)

队列:
  chat:ws:ws-1   ← WS 实例 1 启动时创建，绑定 routing key = ws.ws-1
  chat:ws:ws-2   ← WS 实例 2 启动时创建，绑定 routing key = ws.ws-2

Redis 路由表:
  router:user:10001  → "ws-1"   ← 用户 10001 连到了 WS-1
  router:user:10002  → "ws-2"   ← 用户 10002 连到了 WS-2
```

### 消息投递流程

```
A (连在 WS-1) 给 B (连在 WS-2) 发消息:

  HTTP API Server:
    1. 处理消息（过滤、存 MongoDB）
    2. 查 Redis: GET router:user:B → "ws-2"
    3. MQ publish(routing key = ws.ws-2, payload = { messageId, ... })

  WS-2 实例:
    1. Consumer 从 chat:ws:ws-2 消费
    2. 查本地 ConcurrentHashMap: B 是否在线
    3. 查 B 当前会话: session.getAttributes("currentConversation")
    4. 在该会话 → push "message" + 更新游标
       不在    → push 轻量通知 "new_message"
```

### 群聊

```
HTTP API Server:
  1. 存 MongoDB
  2. 查 group_member 获取所有成员
  3. 对每个成员:
      GET router:user:{userId} → wsInstanceId
  4. 按 wsInstanceId 分组
  5. 每个 wsInstanceId 发一条 MQ publish(routing key = ws.{wsInstanceId}, payload = { members: [...], messageId, ... })

WS 实例消费:
  收到消息 → 遍历 payload 中的 members → 各自判断是否在线/在会话 → 推送
```

### 单实例时

```
当前单实例 (ws-1) 也走同样流程:
  - 队列: chat:ws:ws-1
  - Redis: router:user:{id} → "ws-1"
  - 自己发 → 路由到 ws-1 → 自己消费

  逻辑完全一致，后续加实例无需改代码
```

---

## 5. 用户 Feed（MongoDB 读扩散）

每个用户每个会话的 Feed 存在 MongoDB，只存消息 ID，不存正文：

```
Collection: message_feed

Document:
{
  "userId": 10001,
  "conversationId": "p_10001_10002",
  "messageId": "BD8U-FCOJ-LDC5-L789",
  "createdAt": 1716123456789
}

索引:
  { userId: 1, conversationId: 1, messageId: -1 }  ← 80-bit 时间戳在高位，天然有序
```

### 为什么不用 Redis ZSET

- 群聊 500 人 × 每天 1000 条消息 = 50 万条/天，Redis 内存扛不住
- MongoDB 分片天然支持海量数据
- Feed 只存 messageId（19 字符），不存消息正文

### Feed 读写

```
① 写 Feed（Consumer 异步）
   A 发消息给群 g_10000:
     Consumer 遍历所有成员 M:
       message_feed.insertOne({ userId: M, conversationId: g_10000, messageId, createdAt })

② 读 Feed（HTTP 拉取）
   GET /api/conversations/g_10000/messages?cursor=&limit=20
     cursor 为空 → find({ userId: me, conversationId: g_10000 })
                    .sort({ messageId: -1 }).limit(20)
     cursor = "BD8U-xxx" → find({ userId: me, conversationId: g_10000,
                                   messageId: { $lt: "BD8U-xxx" } })
                            .sort({ messageId: -1 }).limit(20)
     拿到 messageId 列表 → 批量查 messages 集合取正文
     → 返回 { list, nextCursor, hasMore }

③ 标记已读（Redis 轻量）
   PUT /api/conversations/{convId}/read
   → SET cursor:me:{convId} "BD8U-FCOJ-LDC5-L789"  (read_cursor = 最新已读消息ID)
   → HSET chat:session:me:{convId} unread_count 0
```

**未读数计算**：比较 messageId。80-bit ID 自带时间有序，直接字符串比较 `>{read_cursor}` 即可计算新消息数。

---

## 6. 私聊消息发送流程

```
POST /api/conversations/p_A_B/messages
  { content: "你好", messageType: "text" }

MessageService:
  1. 验证 token，提取 userId = A
  2. 验证 A 是会话参与者
  3. 校验: A 和 B 是双向好友 + 没有被拉黑
  4. 消息过滤链
  5. INCR msgid:spin:p_A_B → spin (12bit)
  6. messageId = generateMessageId(convId, type)  // 80-bit
  7. MongoDB insertOne(消息文档)
  8. 查 Redis: GET router:user:B → "ws-1"
  9. MQ publish(routing key = ws.ws-1, payload = { messageId, ... })
  10. 返回 { messageId, createdAt } 给 A

WS-1 Consumer:
  1. MongoDB insert feed: { userId: B, ... }
  2. 查本地 sessions: B 在线?
     ├─ 在 p_A_B 中 → push "message" + 更新 cursor
     ├─ 在线不在会话 → push "new_message"
     └─ 离线 → 跳过
  3. 不写 Redis 摘要
```

---

## 7. 群聊消息发送流程（异步）

```
POST /api/conversations/g_10000/messages
  { content: "大家好", messageType: "text" }

MessageService:
  1-7. 同上（验证 + 生成ID + 存MongoDB）
  8. 查 group_member 获取所有成员
  9. 对每个成员查 Redis: GET router:user:{uid} → wsInstanceId
  10. 按 wsInstanceId 分组，对每个分组:
       MQ publish(routing key = ws.{wsInstanceId},
         payload = { members: [uid1, uid2, ...], messageId, ... })
  11. 立即返回 ack

各 WS Consumer:
  收到消息 → 遍历 payload.members → 各自查本地 sessions:
    ├─ 在线且在群会话中 → push "message" + 更新 cursor
    ├─ 在线不在会话   → push "new_message"
    └─ 离线          → 跳过
```

---

## 8. 消息历史（游标分页）

```
GET /api/conversations/{convId}/messages?cursor=&limit=20

后端逻辑:
  1. cursor 为空 → message_feed.find({ userId: me, conversationId: convId })
                    .sort({ messageId: -1 }).limit(20)
     cursor = "BD8U-xxx" → .find({ userId: me, conversationId: convId,
                                    messageId: { $lt: cursor } })
                            .sort({ messageId: -1 }).limit(20)

  2. 拿到 messageId 列表 → messages.find({ messageId: { $in: [...] } })

  3. 返回 { list: [...], nextCursor, hasMore: bool }
```

---

## 9. 消息端到端流程总结

```
┌──────────────────────────────────────────────────────────────────┐
│ A 发消息 "你好"给 B                                                │
│                                                                   │
│ HTTP POST → MessageService                                       │
│   │                                                               │
│   ├─ INCR spin → generateMessageId → "BD8U-..."                   │
│   ├─ MongoDB insert                                               │
│   ├─ MQ publish (user.B)                                          │
│   └─ return { messageId, createdAt }                              │
│                                                                   │
│ Consumer (异步)                                                    │
│   │                                                               │
│   ├─ MongoDB insert feed { userId:B, convId, msgId, seq }        │
│   └─ B 在线且在会话中? → WS push + 更新 Redis 游标                 │
│       不在会话中但在线? → WS push 轻量通知                         │
│       离线? → 什么都不做                                           │
│                                                                   │
│ B 打开会话列表（懒加载）                                            │
│   │                                                               │
│   ├─ MongoDB aggregate: 每个会话最新 feed 条目 + 未读数            │
│   ├─ 写入/刷新 Redis 会话摘要（缓存）                              │
│   └─ 返回前端渲染                                                   │
│                                                                   │
│ B 进入会话                                                         │
│   │                                                               │
│   ├─ 查 Redis read_cursor（没有则从 MongoDB feed 推算）           │
│   ├─ GET /messages?cursor=  → feed → MongoDB 查消息正文           │
│   └─ WS 进入会话模式: 后续新消息直接推送 + 实时更新游标               │
└──────────────────────────────────────────────────────────────────┘
```

---

## 10. 懒加载 Redis 会话摘要

### 为什么不每条消息都写 Redis

```
群聊 500 人 × 每天 1000 条消息 = 500人 × 1000次 Redis 更新/天
其中 495 人根本没在看 → 浪费
```

### 懒加载机制

```
Redis 会话摘要是缓存，不是数据源。
数据源是 MongoDB message_feed。

会话摘要的写入时机:
  ✅ 用户打开会话列表（GET /api/conversations）
  ✅ 用户进入某个会话（GET /api/conversations/{id}/messages）
  ❌ 每条消息到达时（不写）

会话摘要的数据来源:
  - last_msg_content / last_msg_time → MongoDB feed 最新一条
  - unread_count → COUNT(feed seq > read_cursor)
  - read_cursor → Redis 有则取，没有则返回空（从头开始拉）
```

### 游标更新时机

```
✅ 用户在会话页面中，新消息到达 → WS 实时更新 Redis read_cursor
✅ 用户发送消息 → WS push 给发送者更新 read_cursor（自己发的已读）
✅ 用户滚动消息列表 → 前端发 mark_read → 更新 Redis read_cursor
❌ 用户不在会话页面中 → 不更新
```

### WS 连接 → 会话绑定

```
WebSocket 连接建立后，前端告知当前查看的会话:

客户端 → 服务端:
  { "action": "enter_conversation", "data": { "conversationId": "p_A_B" } }
  
服务端:
  - 记录: 用户 X 当前在会话 Y
  - 后续该会话的消息直接推送 + 更新游标

客户端切换会话/离开:
  { "action": "leave_conversation", "data": { "conversationId": "p_A_B" } }
```

---

## 11. 在线状态（Redis TTL + 应用层心跳）

### 设计

```
Redis Key:  online:user:{userId}
Value:      "1"
TTL:        60 秒

连接时:
  SET online:user:10001 "1" EX 60
  + sessions.put(10001, session)   ← 本地 ConcurrentHashMap

心跳 (每30秒):
  前端 → { action: "ping" }
  后端 → EXPIRE online:user:10001 60    ← 续期
  后端 → { action: "pong" }

正常断开:
  @OnClose → DEL online:user:10001
           → sessions.remove(10001)

异常断开 (网络闪断、浏览器崩溃):
  TTL 60s 到期 → Redis 自动删除 → 标记离线
  无需人工干预
```

### 为什么两层都有

| | 浏览器 WS 内置 ping/pong | 应用层心跳 |
|------|------|------|
| 作用 | 保持 TCP 连接存活 | 续期 Redis 在线标记 |
| 频率 | 由浏览器控制 | 应用层控制（30s） |
| 对业务影响 | 无 | 刷新 `online:user:{id}` TTL |

内置 ping/pong 管连接，应用层心跳管状态，各管各的。

### Consumer 查在线状态

```java
// 发消息时判断是否推送
Boolean online = redisTemplate.hasKey("online:user:" + userId);
if (online) {
    WebSocketSession session = sessions.get(userId);
    if (session != null && session.isOpen()) {
        // 在线，检查是否在当前会话中
        pushOrNotify(session, userId, msg);
    }
}
// offline → 跳过，消息已在 MongoDB，用户上线后拉取
```

### 多实例扩展

```
当前单实例: ConcurrentHashMap + Redis 就够了
后续多实例: Redis 中存 "ws-1" 或 "ws-2"，MQ consumer 根据值转发到对应节点
```

---

## 12. 会话绑定（WS Session 属性 + ConcurrentHashMap）

### 存储

```java
public class ChatWebSocketHandler {

    // 用户 → WS 连接
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 连接建立
    @OnOpen
    void onOpen(WebSocketSession session) {
        Long userId = verifyToken(session);
        session.getAttributes().put("userId", userId);
        sessions.put(userId, session);
    }

    // 进入会话
    @OnMessage("enter_conversation")
    void onEnter(WebSocketSession session, String convId) {
        session.getAttributes().put("currentConversation", convId);
    }

    // 离开会话
    @OnMessage("leave_conversation")
    void onLeave(WebSocketSession session) {
        session.getAttributes().remove("currentConversation");
    }
}
```

### 消费 MQ 消息时判断推送策略

```java
public void onMqMessage(Message msg) {
    for (Long memberId : getConversationMembers(msg.conversationId)) {
        WebSocketSession session = sessions.get(memberId);
        if (session == null || !session.isOpen()) continue; // 离线

        String viewing = (String) session.getAttributes().get("currentConversation");
        if (msg.conversationId.equals(viewing)) {
            // 在该会话中 → 推送消息 + 更新 Redis read_cursor
            session.send(pushMessage(msg));
            redisTemplate.opsForValue().set(
                "cursor:" + memberId + ":" + msg.conversationId, msg.messageId);
        } else {
            // 在线但不在该会话 → 轻量通知
            session.send(lightNotify(msg));
        }
    }
}
```

### 数据分布

| 数据 | 存放位置 | 生命周期 | 用途 |
|------|---------|---------|------|
| 用户→WS连接 | ConcurrentHashMap | 连接存活期 | 查找在线用户的连接 |
| 当前查看的会话 | WS Session Attributes | 连接存活期 | 判断推送策略 |
| read_cursor | Redis | 跨连接持久 | 用户重连后恢复阅读位置 |
| 在线标记 | Redis TTL | 心跳 60s | 跨节点判断在线 |
| 消息 Feed | MongoDB | 永久 | 消息数据源 |
| 会话摘要 | Redis(懒加载缓存) | 按需刷新 | 会话列表渲染 |

---

## 13. 接口一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/conversations/{convId}/messages` | 发送消息 |
| GET | `/api/conversations/{convId}/messages` | 消息历史（游标分页） |
| PUT | `/api/conversations/{convId}/read` | 标记已读 |
| WS | `/ws?token=xxx` | WebSocket 连接 |

### WS 协议完整列表

```json
// ===== 客户端 → 服务端 =====
{ "action": "ping" }
{ "action": "enter_conversation", "data": { "conversationId": "p_A_B" } }
{ "action": "leave_conversation", "data": { "conversationId": "p_A_B" } }
{ "action": "send_message", "data": { "conversationId": "p_A_B", "content": "你好", "messageType": "text" } }
{ "action": "mark_read", "data": { "conversationId": "p_A_B" } }

// ===== 服务端 → 客户端 =====
{ "action": "pong" }
{ "action": "message", "data": { "messageId": "p_A_B#3", "conversationId": "p_A_B", "fromUserId": 10001, "content": "你好", "messageType": "text", "createdAt": 1716123456789 } }
{ "action": "new_message", "data": { "conversationId": "p_A_B", "preview": "你好", "senderName": "张三" } }
```

---

## 14. 消息过滤链（Chain of Responsibility + AC 自动机）

### 设计模式

```
                     ┌─────────────┐
                  →  │  Filter 1   │ →
  Message ──→       │  (关键字过滤) │      →  Message
                     └─────────────┘        (可能被修改/拦截)
                          ↓ reject
                       返回拒绝
```

- 每个 Filter 实现 `MessageFilter` 接口，有 `filter(Message)` 方法
- Filter 可以修改消息内容、也可以抛出异常拦截
- Filter 链按顺序执行，前一个的输出是后一个的输入
- 配置化：可通过配置增删 Filter

### AC 自动机（Aho-Corasick）

用于高效多模式匹配。在 `KeywordFilter` 内部使用：

```
敏感词库: ["广告", "违禁词1", "违禁词2", ...]

AC 自动机:
  1. 构建 Trie 树
  2. 构建 fail 指针
  3. 一次扫描匹配所有模式

匹配到敏感词 → 替换为 ***
```

### 实现结构

```java
// 接口
public interface MessageFilter {
    MessageContent doFilter(MessageContent content) throws FilterRejectException;
}

// 链
public class MessageFilterChain {
    private List<MessageFilter> filters;
    
    public MessageContent execute(MessageContent content) {
        for (MessageFilter f : filters) {
            content = f.doFilter(content);
        }
        return content;
    }
}

// AC 自动机关键词过滤器
public class SensitiveWordFilter implements MessageFilter {
    private AhoCorasick ac;
    
    public MessageContent doFilter(MessageContent content) {
        String text = content.getContent();
        List<MatchResult> hits = ac.search(text);
        for (MatchResult hit : hits) {
            text = text.replace(hit.word, "***");
        }
        content.setContent(text);
        return content;
    }
}
```

### 链配置

```yaml
message:
  filters:
    - sensitiveWord      # 敏感词过滤（AC自动机）
    - lengthLimit        # 长度限制（>5000字拒绝）
    - antiSpam           # 刷屏检测（预留）
```

---

## 12. 新增依赖

- `spring-boot-starter-amqp` (RabbitMQ)
- `spring-boot-starter-data-mongodb`
- `spring-boot-starter-websocket`
