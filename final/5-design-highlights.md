# HappyIM 设计亮点与决策记录

## 1. 80-bit 消息 ID

### 设计

```
┌──────────┬──────────┬───────┬──────────┐
│ timestamp │  spin   │ type  │   CRC22  │
│  (42bit)  │ (12bit) │ (4bit)│  (22bit) │
└──────────┬┴─────────┴───────┴──────────┘
           └── Base32 编码 → "XXXX-XXXX-XXXX-XXXX"
```

### 为什么这样设计？

- **高位时间戳**：字符串字典序天然等于时间序，排序、分页、游标比较全部基于字符串
- **不用 UUID**：UUID v4 随机无序，B-Tree 索引性能差，且不能用作游标
- **不用雪花算法**：64-bit 太短，群聊高并发场景可能有碰撞风险；80-bit 多一层安全保障
- **CRC22 仅做分片**：22-bit 覆盖约 400 万分片，足够用；不用于反查会话 ID

### 优势

- 全局自然有序，前端可直接 `localeCompare` 排序
- 字符串兼容性好，所有数据库都能高效索引
- 游标分页不需要额外的 `created_at` 字段

---

## 2. 未读计数：游标 → 计数器的演进

### v1 方案（游标对比）

```
每次查未读: MongoDB count(messageId > read_cursor)
```

**问题**：会话多时，每个会话一次 MongoDB count 查询，性能差。

### v2 方案（整数计数器）

```
新消息 → HINCRBY unread_count 1
打开会话 → SET unread_count 0
查未读 → 直接从 Redis Hash 读
```

**为什么保留 `read_cursor`？** 计数器能告诉你有几条未读，但不能告诉用户"从哪条开始是新的"。`read_cursor` 用于后续"跳到第一条未读"功能。

### 并发安全

- `HINCRBY` 是 Redis 原子操作，不存在竞态
- Consumer 归零 + API 端 +1 可能交错 → 但 `SET 0` 也是原子的，以最后操作为准

---

## 3. 用户信息本地缓存

### 传统做法

每条消息携带 `{ fromUserId, fromNickname, fromAvatar }`，服务端每次都要拼装这些字段。

### 我们的做法

消息只带 `fromUserId`，前端维护全局 `userCache`。

```
userCache.setAll(friends)     ← 好友列表加载时灌入
userCache.batchFetch(uids)    ← 消息中遇到陌生 uid，300ms 合并请求
userCache.displayName(uid, gid) ← 前端本地计算: remark > groupNickname > nickname
```

### 优势

- 减少网络传输：不带昵称头像字段，消息体更小
- 减少请求次数：批量合并 + 缓存命中
- 数据一致性好：备注改名后所有会话同时生效

### 群昵称按需加载

不加载整个群成员列表，而是收到某人的群消息时才 `GET /groups/{gid}/members/{uid}` 获取该人的群昵称。

---

## 4. 朋友圈/广场：嵌入式文档 vs 关系型

### 为什么用 MongoDB 嵌入式数组存储 likes 和 comments？

```
点赞: $push { likes: { userId, nickname, createdAt } }
取消: $pull { likes: { userId } }
```

- **原子性**：一次 MongoDB 操作完成，无需事务
- **读取效率**：一次查询拿完整帖子，无需 JOIN
- **容量安全**：朋友圈场景下，每个帖子点赞+评论通常 < 1000，远不到 16MB

### 为什么通知单独一个集合？

- 通知是"行为日志"，更适合独立查询和清理
- 支持分页和 TTL 索引自动过期
- 不跟帖子生命周期绑定

---

## 5. WebSocket 分享连接

### 为什么不用每个页面各自连 WS？

```
useWebSocket()  // 模块级单例
├── MainLayout: connect() + 系统通知监听
├── ChatPage:    onMessage() 注册消息处理器
├── MomentsPage: onMessage() 注册朋友圈通知
└── SquarePage:  onMessage() 注册广场通知
```

- 只有一个连接，心跳开销最小
- `onMessage()` 返回取消注册函数，组件卸载时自动清理
- `connected` 响应式状态，侧边栏实时显示绿点/红点

---

## 6. RabbitMQ 消息投递：尽力而为

```
API → MongoDB (持久化) ✓
API → RabbitMQ (推送)   ← 失败不阻塞
```

- 消息先存 MongoDB，再投 MQ
- MQ 投递失败 → 仅打日志，不影响 HTTP 返回
- 接收方未收到实时推送 → 下次打开会话从 Feed 拉取，不影响消息可达性

---

## 7. 音频提示音：Web Audio API 合成

不用音频文件，纯代码合成：

```javascript
const osc = audioCtx.createOscillator()
osc.type = 'sine'        // 波形: sine / triangle / square
osc.frequency.value = 800 // 频率
gain.gain.setValueAtTime(0.15, now)      // 音量
gain.gain.exponentialRampToValueAtTime(0.001, now + 0.1) // 衰减
```

- 零网络请求，零加载时间
- 四种不同音色区分消息类型
- `isMuted()` 全局静音控制

---

## 8. 通知替代轮询

### 之前

```javascript
setInterval(fetchPendingCount, 30000)   // 30秒轮询好友请求
setInterval(fetchMomentNotices, 30000)  // 30秒轮询朋友圈通知
```

### 之后

```
FriendService/MomentService/SquareService
  → RabbitMQ (friend_notify / moment_notify / square_notify)
  → MessageConsumer.handleEvent()
  → ChatWebSocketHandler.pushEvent(targetUserId)
  → 前端 onMessage → 更新红点
```

- 零延迟：消息到达即时更新
- 零浪费：没事件就不请求，不消耗带宽

---

## 9. 暗色模式五级色板

暗色模式下不同区块用不同深度的颜色，建立视觉层次：

```
导航栏  → #0d0d1c (最深)
侧边栏  → #141428
主背景  → #1a1a30
卡片    → #262644
输入框  → #2e2e4c (最浅)
```

而不是所有区域同一个颜色平铺，避免"一片黑"的廉价感。
