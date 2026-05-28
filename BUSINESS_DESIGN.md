# HappyIM 业务模块分析

## 📋 核心业务模块（按优先级）

### 第1层：基础服务
```
1. 用户认证服务 (User Service)
   - 用户注册/登录
   - 用户信息管理
   - 会话管理
   存储: MySQL
   
2. WebSocket 连接服务 (Connection Service)
   - 管理用户在线状态
   - 维护连接映射表
   - 心跳检测
   存储: Redis (会话状态)
```

### 第2层：关系管理服务
```
3. 好友服务 (Friend Service)
   - 好友申请 (待审批/已申请/已拒绝)
   - 好友列表
   - 好友删除
   - 黑名单管理
   存储: MySQL
   
4. 群聊服务 (Group Service)
   - 群创建/删除
   - 群成员管理
   - 群信息编辑
   - 群管理员权限
   - 群公告
   存储: MySQL
```

### 第3层：消息服务
```
5. 消息服务 (Message Service)
   - 一对一消息
   - 群消息
   - 消息存储
   - 消息撤回
   - 消息已读状态
   - 幂等性处理 (防重ID)
   存储: MongoDB
   
6. 消息队列服务 (Message Queue)
   - RabbitMQ 队列管理
   - 消息路由
   - 离线消息存储
   存储: RabbitMQ + MongoDB
```

### 第4层：业务逻辑服务
```
7. 聊天消息管理 (Chat Manager)
   - 消息历史查询
   - 消息搜索
   - 聊天清空
   - 按时间/内容过滤
   
8. 消息推送服务 (Push Service)
   - 在线推送
   - 离线推送队列
   - 消息去重
   - 消息确认
```

---

## 🗄️ 数据库设计

### MySQL 表结构

```sql
-- 1. 用户表
user (
  id PK
  username unique
  password hash
  avatar url
  signature text
  status enum(online/offline/invisible)
  created_at
  updated_at
)

-- 2. 好友表
friend (
  id PK
  from_user_id FK -> user
  to_user_id FK -> user
  status enum(pending/accepted/rejected/blocked)
  request_msg text
  requested_at
  accepted_at
  created_at
)

-- 3. 黑名单表
blacklist (
  id PK
  user_id FK -> user
  blocked_user_id FK -> user
  reason text
  created_at
)

-- 4. 群聊表
group_chat (
  id PK
  name
  owner_id FK -> user
  avatar url
  notice text
  member_count
  created_at
  updated_at
)

-- 5. 群成员表
group_member (
  id PK
  group_id FK -> group_chat
  user_id FK -> user
  role enum(owner/admin/member)
  muted boolean
  joined_at
)

-- 6. 群管理员权限表
group_permission (
  id PK
  group_id FK -> group_chat
  permission_type enum(manage_members/manage_info/post_announcement)
  created_at
)

-- 7. 消息防重表
message_dedup (
  id PK
  dedup_id unique (业务生成的幂等ID)
  message_hash
  created_at
  expired_at (24小时过期)
)
```

### MongoDB 集合结构

```json
// 1. 聊天消息集合
db.messages {
  _id: ObjectId
  message_id: string (拼接ID: conversation_id_timestamp_seq) [unique, indexed]
  conversation_id: string (user1_user2 or group_123)
  conversation_type: "single" | "group"
  from_user_id: string
  to_user_id: string (one-to-one only)
  group_id: string (group only)
  content: string
  message_type: "text" | "image" | "video" | "file" | "recall"
  status: "sent" | "delivered" | "read" | "recalled"
  read_by: [         // 已读用户列表（群聊）
    {user_id, read_at}
  ]
  created_at: timestamp
  updated_at: timestamp
  recalled_at: timestamp (if recalled)
  recalled_by: user_id (if recalled)
  
  indexes:
  - {message_id} unique
  - {conversation_id, created_at}  // 快速查询会话消息
  - {from_user_id, created_at}
  - {group_id, created_at}
  - {created_at} (ttl: 365 days)
}

// 2. 消息已读状态集合
db.message_read_status {
  _id: ObjectId
  message_id: string
  group_id: string (only for group)
  read_by: [
    {user_id, read_at}
  ]
}

// 3. 聊天会话集合（冷数据，主要用Redis）
db.chat_sessions {
  _id: ObjectId
  user_id: string
  conversation_id: string
  conversation_type: "single" | "group"
  last_message_id: string
  read_cursor: string  // 游标位置
  updated_at: timestamp
}
```

---

## 🔄 消息流程图

### 一对一消息流程（修正版）
```
User A (Browser)
    ↓ WebSocket send
Backend (Receive HTTP)
    ↓ 1. 幂等性检查 (dedup_id)
    ├─ 2. Push to RabbitMQ(chat_user_b)
    ├─ 3. WebSocket监听queue → 消费消息
    ├─ 4. 存储到MongoDB
    └─ 5. 更新Redis聊天摘要 + 游标
    ↓
User B
    ├─ 在线: WebSocket实时接收消息
    └─ 离线: 消息持久化在MongoDB
    
User B上线时:
    ├─ 查询Redis摘要获取游标位置
    ├─ 从MongoDB查询游标之后的消息
    └─ 一次性推送给前端
```

### 群聊消息流程（修正版）
```
User A (Browser)
    ↓ WebSocket send
Backend (Receive HTTP)
    ↓ 权限检查 + 幂等性检查
    ├─ Push to RabbitMQ(chat_group_x) → 所有群成员队列
    ├─ WebSocket消费 → MongoDB存储
    └─ 更新所有成员的Redis摘要 + 游标
    ↓
所有群成员
    ├─ 在线: WebSocket实时接收
    └─ 离线: MongoDB存储等待查询
```

### 消息撤回流程
```
User A 发送撤回指令
    ↓
Backend (Receive)
    ├─ 权限检查 (自己的消息 or 管理员)
    ├─ 时间检查 (2小时内)
    ├─ 更新 MongoDB: status = "recalled"
    ├─ 广播撤回事件给所有接收者
    └─ 已离线的用户 → RabbitMQ queue 入队
```

---

## 🛡️ 防重幂等性设计

### 前端生成 dedup_id
```
dedup_id = uuid() 或 ${userId}_${timestamp}_${random}
```

### 后端处理
```
1. 接收消息时生成 dedup_id
2. 查询 message_dedup 表
   ├─ 存在? → 返回已存在的消息ID（幂等）
   └─ 不存在? → 创建消息，插入 dedup_id
3. 清理过期的 dedup_id (24h)
```

### 数据库层唯一约束
```
message_dedup table:
  unique key: dedup_id
  TTL: 24小时
```

---

## 📌 会话ID与消息ID设计

> 详见 [CONVERSATION_ID_AND_REDIS_DESIGN.md](CONVERSATION_ID_AND_REDIS_DESIGN.md)

### 会话ID结构

```
格式: {type}_{identifier}

类型前缀:
  p_  私聊 (personal)    例: p_user001_user002  (两个用户ID按字典序排序)
  g_  群聊 (group)       例: g_group1730000123
  b_  机器人 (bot)        例: b_user001_weather_bot
  s_  系统通知 (system)   例: s_user001
```

### 消息ID

融云风格 80-bit 编码，详见 [RONGCLOUD_MESSAGE_ID.md](RONGCLOUD_MESSAGE_ID.md)。

### Redis会话摘要

```
1. ZSET — 会话时间线
   Key:  chat:sessions:{userId}
   Score: last_message_time
   Member: conversation_id

2. ZSET — 置顶会话
   Key:  chat:sessions:pinned:{userId}
   Score: pinned_at
   Member: conversation_id

3. Hash — 单个会话详情
   Key:  chat:session:{userId}:{conversationId}
   Fields: type, peer_id, peer_name, peer_avatar,
           last_msg_id, last_msg_content, last_msg_type,
           last_msg_time, last_sender_id, unread_count,
           read_cursor, is_muted, updated_at
```

### 消息游标机制

```
① User B 上线
   ├─ 连接WebSocket
   ├─ HGET chat:session:userB:{conversationId} read_cursor
   └─ 获取 read_cursor

② 查询MongoDB消息
   ├─ Query: {
       conversation_id: "p_userA_userB",
       message_id: { $gt: read_cursor }
     }
   └─ 返回未读消息列表

③ 推送给前端
   ├─ 一次性推送所有未读消息
   └─ HSET read_cursor = 最新消息ID

④ 消息已读
   ├─ 前端发送已读确认
   ├─ HSET unread_count = 0, read_cursor = 最新消息ID
   └─ 异步更新MongoDB已读状态
```

---

## 📊 服务交互关系图

```
┌─────────────────┐
│  User Service   │ ← 用户认证/注册
└────────┬────────┘
         │
    ┌────┴────┐
    │          │
┌───▼──────┐ ┌─┴─────────────┐
│ Friend   │ │ Group Service │
│ Service  │ └─┬──────────────┘
└───┬──────┘   │
    │          │
    └────┬─────┘
         │
    ┌────▼──────────────┐
    │ Message Service   │
    │ (核心业务逻辑)     │
    └────┬──────────────┘
         │
    ┌────┴────────────────────┐
    │                         │
┌───▼────────────┐   ┌────────▼──────┐
│ MongoDB        │   │ RabbitMQ      │
│ (消息存储)      │   │ (消息队列)     │
└────────────────┘   └───────────────┘

同步:
┌──────────────┐
│ Redis        │ (在线状态、会话缓存)
└──────────────┘
```

---

## ✅ 功能清单（按开发顺序）

### Phase 1: MVP（基础）
- [ ] 用户注册/登录（MySQL）
- [ ] WebSocket 连接管理
- [ ] 一对一消息（MySQL写，MongoDB读）
- [ ] 消息防重（dedup_id）
- [ ] 基础好友关系

### Phase 2: 扩展
- [ ] 好友申请/删除
- [ ] 黑名单功能
- [ ] 群聊基础（创建/删除/成员管理）
- [ ] 群消息
- [ ] 消息撤回

### Phase 3: 高级
- [ ] 消息已读状态
- [ ] 聊天记录查询
- [ ] 消息搜索
- [ ] 群公告
- [ ] 群管理权限系统

---

## 🔗 关键技术点

| 模块 | 技术方案 | 原因 |
|------|--------|------|
| 用户认证 | JWT token | 无状态，分布式友好 |
| 会话管理 | Redis | 高速缓存在线状态 |
| 消息队列 | RabbitMQ | 异步处理，离线消息存储 |
| 消息存储 | MongoDB | 文档型，灵活的消息模式 |
| 用户数据 | MySQL | 事务性强，关系清晰 |
| 防重 | 双层检查 | 数据库唯一约束 + 内存缓存 |
| 消息推送 | WebSocket | 双向通信，实时性好 |

