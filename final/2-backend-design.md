# HappyIM 后端设计文档

## 1. 认证与安全

### JWT 双令牌机制

```
Access Token  (15 分钟) — 用于 API 鉴权
Refresh Token (7 天)    — 用于刷新 Access Token
```

- 登录时返回两个 token
- Access Token 过期 → 前端拦截器自动用 Refresh Token 换新的
- Refresh Token 存储在 MySQL 和 Redis 双写，支持主动失效

### 登录流程

```
邮箱 + 密码 + 验证码
       │
       ▼
 POST /api/auth/login
       │
       ├── 验证邮箱验证码 (Redis TTL 5分钟)
       ├── BCrypt 校验密码
       ├── 生成 JWT Token Pair
       └── 返回 { accessToken, refreshToken, userInfo }
```

### 权限拦截

`@LoginRequired` 注解 + `AuthInterceptor` 拦截器：
- 从 `Authorization: Bearer {token}` 提取 JWT
- 校验签名和过期时间
- 将 `userId` 注入请求上下文

## 2. 用户体系

### ID 生成 (Leaf 号段模式)

```sql
-- id_segment 表
INSERT INTO id_segment (biz_tag, max_id, step) VALUES ('user', 20021228, 1000);
```

- 服务启动时预取号段 [max_id, max_id + step]，缓存在内存
- 99.9% 的场景从本地内存取 ID，不查 Redis/DB
- 号段耗尽时原子更新 `max_id = max_id + step`

### 用户注册

```
用户名 (5位以上字母数字下划线) + 邮箱 + 密码
       │
       ▼
 1. 校验用户名/邮箱唯一性
 2. 发送邮箱验证码 (6位数字, Redis TTL 5分钟)
 3. BCrypt 加密密码
 4. 号段分配 userId
 5. INSERT INTO user
```

## 3. 好友系统

### 数据模型

```
friend 表 (双向存储):
  user_id, friend_id, remark, is_starred

blacklist 表 (单向):
  user_id, blocked_user_id

friend_request 表:
  from_user_id, to_user_id, message, status (0=待处理/1=同意/2=拒绝)
```

### 好友关系维护

| 操作 | 行为 |
|------|------|
| 发送申请 | INSERT 申请，若已有待处理则覆盖 |
| 同意申请 | 双向写入 friend 表 + 系统消息 "你们已成为好友" |
| 删除好友 | 单向删除 (DELETE user_id→friend_id) |
| 拉黑 | INSERT blacklist，不删除好友关系 |
| 星标 | UPDATE is_starred |

### 设计要点

- **双向写入、单向删除**：A 删 B，B 的列表里 A 依然存在，下次聊天自动重建
- **拉黑不删好友**：拉黑是附加状态，不影响好友关系本身
- **重复申请覆盖**：避免申请列表堆积

## 4. 群聊系统

### 数据模型

```
group_chat:  id, name, avatar_url, notice, description, allow_invite, owner_id
group_member: id, group_id, user_id, role (1=群主/2=管理/3=成员), group_nickname
```

### 群操作权限矩阵

| 操作 | 群主 | 管理 | 成员 |
|------|:----:|:----:|:----:|
| 修改群名称/简介 | ✓ | ✓ | ✗ |
| 修改群头像 | ✓ | ✓ | ✗ |
| 发布群公告 | ✓ | ✓ | ✗ |
| 邀请成员 | ✓ | ✓ | 取决于 allow_invite |
| 踢人 | ✓ | ✓(非管理) | ✗ |
| 设置管理员 | ✓ | ✗ | ✗ |
| 转让群主 | ✓ | ✗ | ✗ |
| 解散群 | ✓ | ✗ | ✗ |
| 修改自己群昵称 | ✓ | ✓ | ✓ |

### 群消息路由

```
Sender → API → MongoDB → MQ → WS Consumer
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
                Member A       Member B       Member C
              (自己跳过)    (not viewing)  (viewing: push full msg)
                               │              │
                               ▼              ▼
                          通知 + unread+1   完整消息 + unread=0
```

## 5. 消息系统

### 消息写入流程

```
1. validateParticipant()    — 校验好友关系/群成员/黑名单
2. filterChain.execute()    — 敏感词过滤 (AC 自动机)
3. idGenerator.generate()   — 80-bit 消息 ID
4. mongoTemplate.insert()   — 写入 messages 集合
5. writeFeeds()             — 写入 message_feed (每参与者一份)
6. incrementUnread()        — 接收者 Redis unread_count +1
7. updateSessionLastMsg()   — 更新所有参与者 session hash
8. rabbitTemplate.send()    — 投递 MQ
9. 文件消息 → file_feed
```

### 消息 ID 设计

```
80 bits:
┌──────────────┬──────────┬───────┬──────────┐
│ timestamp(42)│ spin(12) │ type(4)│ CRC22(22)│
└──────────────┴──────────┴───────┴──────────┘

Base32 编码: XXXX-XXXX-XXXX-XXXX (16 chars)
```

- 高位时间戳保证全局有序，字符串字典序 = 时间序
- spin 同一毫秒内自增序列号 (Redis INCR)
- CRC22 用于消息分片路由

### 未读计数方案

采用 **unread_count 整数计数器** + **read_cursor 游标** 双字段：

| 字段 | 位置 | 用途 |
|------|------|------|
| `unread_count` | Redis Hash | 会话列表红点数字 |
| `read_cursor` | Redis Hash | 用户最后读到哪条消息 (messageId) |

- 新消息 → `HINCRBY unread_count 1` (原子操作)
- 打开会话 → `SET unread_count 0` + `SET read_cursor = 最新messageId`
- Consumer 推送 → 正在看此会话 → 归零；未在看 → 仅推送通知

**为什么不用纯游标计数？** 游标需要 MongoDB `count(messageId > cursor)` 查询，会话多时性能差。改为 `unread_count` 整数直接读取。

## 6. 朋友圈 & 广场

### 数据模型

所有数据存 MongoDB：

```
moments / square_posts 集合:
{
  _id, userId, nickname, avatar,
  content, mediaUrls, visibility,
  likes: [{ userId, nickname, createdAt }],
  comments: [{ userId, nickname, content, replyToUserId, replyToNickname, createdAt }],
  createdAt
}

moment_notifications / square_notifications 集合:
{
  _id, userId, fromUserId, fromNickname, fromAvatar,
  momentId, type ("like"/"comment"/"reply"),
  content, isRead, createdAt
}
```

### 为什么嵌入 likes 和 comments 而不是分表？

- MongoDB 单文档大小限制 16MB，朋友圈点赞评论量远小于上限
- 一次查询拿全部数据，无需 JOIN
- 点赞/评论原子操作 (`$push` / `$pull`)

### 朋友圈 vs 广场

| 维度 | 朋友圈 | 广场 |
|------|--------|------|
| 可见范围 | 好友 + 自己 | 全局 |
| 时间线查询 | `userId IN (friendIds + self)` | 无过滤，全量 |
| 排行榜 | 无 | Redis ZSET 每日活跃 |
| 通知 | 有 | 有 |

## 7. 文件管理

### 上传流程

```
Client → POST /api/files/upload (Multipart)
           │
           ▼
       MinIO putObject()
           │
           ▼
       返回 { url: "minio/path/file.ext" }
           │
           ▼
       发送消息时 content = url, messageType = "file"/"image"/"video"
```

### 文件下载

```
Client → GET /api/files/download/{path}
           │
           ▼
       MinIO getObject() → Streaming Response
       (支持 Range 请求，视频可拖动进度条)
```

### 文件 Feed

消息发送时自动写入 `file_feed` 集合：
- 每个参与者写一份 (userId + messageId 唯一)
- 支持按文件类型、发送者、会话筛选
- 按 messageId 降序排列

## 8. WebSocket 实时推送

### 连接管理

```java
// ChatWebSocketHandler.java
ConcurrentHashMap<Long, WebSocketSession> sessions;
```

- 连接建立 → JWT 验证 → 存入 Map
- 30 秒 Ping/Pong 心跳
- 在线状态：Redis `online:user:{id}` 60s TTL，心跳刷新

### 消息推送路由

```
RabbitMQ Message → MessageConsumer.onMessage()
                        │
                        ├── 私聊: 解析 convId 找 receiver
                        ├── 群聊: 遍历 members
                        │
                        ▼
                   pushToUser()
                        │
                        ├── 用户不在线 → 跳过
                        ├── 正在看此会话 → pushMessage (完整消息) + unread=0
                        └── 在看其他会话 → pushNotification (预览) + unread 保持
```

### Push Event 类型

| Action | 用途 |
|--------|------|
| `message` | 当前会话的完整消息 |
| `new_message` | 其他会话的新消息通知 |
| `event: friend_notify` | 好友申请/同意通知 |
| `event: moment_notify` | 朋友圈通知 |
| `event: square_notify` | 广场通知 |
