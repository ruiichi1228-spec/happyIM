# HappyIM 分布式即时通信系统 - 完整架构设计

## 📐 系统架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端层 (Browser)                         │
│  Vue3 + Vite + WebSocket | 登录界面 | 聊天UI | 用户管理UI      │
└────────────────┬──────────────────────────────────┬─────────────┘
                 │ HTTP API                         │ WebSocket
         ┌───────▼──────────────────────────────────▼────────┐
         │              负载均衡/网关层 (Nginx)               │
         │  - 路由分发                                        │
         │  - SSL/TLS                                        │
         │  - 请求头规范化                                    │
         └───────┬──────────────────────────────────┬────────┘
                 │                                   │
    ┌────────────▼─────────────┐    ┌──────────────▼────────────┐
    │   HTTP API Server        │    │  WebSocket Server         │
    │   (Backend Instance)      │    │  (Backend Instance)       │
    │                           │    │                           │
    │ ┌─────────────────────┐  │    │ ┌─────────────────────┐  │
    │ │ User Service        │  │    │ │ Connection Manager  │  │
    │ │ - 注册/登录         │  │    │ │ - 用户连接映射      │  │
    │ │ - 用户信息管理      │  │    │ │ - 心跳检测          │  │
    │ │ - Token验证         │  │    │ │ - 消息分发          │  │
    │ └─────────────────────┘  │    │ └─────────────────────┘  │
    │                           │    │                           │
    │ ┌─────────────────────┐  │    │ ┌─────────────────────┐  │
    │ │ Message Service     │  │    │ │ Queue Consumer      │  │
    │ │ - 消息发送          │  │    │ │ - 监听RabbitMQ      │  │
    │ │ - 消息验证/防重     │  │    │ │ - 消费队列消息      │  │
    │ │ - 消息存储逻辑      │  │    │ │ - 实时推送          │  │
    │ │ - 游标管理          │  │    │ └─────────────────────┘  │
    │ └─────────────────────┘  │    │                           │
    │                           │    │                           │
    │ ┌─────────────────────┐  │    │                           │
    │ │ Friend Service      │  │    │                           │
    │ │ - 好友申请          │  │    │                           │
    │ │ - 黑名单管理        │  │    │                           │
    │ │ - 好友列表          │  │    │                           │
    │ └─────────────────────┘  │    │                           │
    │                           │    │                           │
    │ ┌─────────────────────┐  │    │                           │
    │ │ Group Service       │  │    │                           │
    │ │ - 群创建/删除       │  │    │                           │
    │ │ - 成员管理          │  │    │                           │
    │ │ - 权限管理          │  │    │                           │
    │ └─────────────────────┘  │    │                           │
    │                           │    │                           │
    │ ┌─────────────────────┐  │    │                           │
    │ │ Router Service      │  │    │                           │
    │ │ - 用户路由查询      │  │    │                           │
    │ │ - 分布式路由表      │  │    │                           │
    │ └─────────────────────┘  │    │                           │
    │                           │    │                           │
    │ ┌─────────────────────┐  │    │                           │
    │ │ ID Generator        │  │    │                           │
    │ │ - 融云消息ID生成    │  │    │                           │
    │ │ - 去重ID生成        │  │    │                           │
    │ └─────────────────────┘  │    │                           │
    └────────────┬─────────────┘    └──────────────┬────────────┘
                 │                                   │
        ┌────────┴───────────────┬──────────────────┴─────┐
        │                        │                        │
    ┌───▼────────┐    ┌─────────▼──────┐    ┌──────────▼──┐
    │  MySQL     │    │   MongoDB      │    │  RabbitMQ  │
    │  用户数据  │    │  消息存储      │    │   消息队列  │
    │  关系数据  │    │  会话数据      │    │  离线缓存   │
    └────────────┘    └────────────────┘    └─────────────┘
        │
    ┌───▼──────────────────────────────────────┐
    │   Redis (缓存层)                          │
    │  - 在线状态映射表                         │
    │  - 用户会话摘要 + 游标                   │
    │  - Token缓存                              │
    │  - 分布式路由表                           │
    └────────────────────────────────────────┘
```

---

## 🗂️ 数据库详细设计

### MySQL 表设计

#### 1. 用户表 (user)

```sql
CREATE TABLE `user` (
    `id` VARCHAR(32) PRIMARY KEY COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
    `email` VARCHAR(128) UNIQUE COMMENT '邮箱',
    `phone` VARCHAR(20) UNIQUE COMMENT '手机号',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `signature` VARCHAR(255) COMMENT '个性签名',
    `nickname` VARCHAR(64) COMMENT '昵称',
    `gender` TINYINT COMMENT '性别: 0-未知, 1-男, 2-女',
    `status` TINYINT DEFAULT 0 COMMENT '账户状态: 0-正常, 1-禁用, 2-注销',
    `last_login_at` TIMESTAMP COMMENT '最后登录时间',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at` TIMESTAMP COMMENT '软删除时间',
    
    INDEX `idx_username` (`username`),
    INDEX `idx_email` (`email`),
    INDEX `idx_phone` (`phone`),
    INDEX `idx_created_at` (`created_at`)
) COMMENT='用户表';
```

#### 2. 用户在线状态表 (user_session)

```sql
CREATE TABLE `user_session` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `token` VARCHAR(255) NOT NULL UNIQUE COMMENT 'JWT Token',
    `device_info` VARCHAR(255) COMMENT '设备信息',
    `ip_address` VARCHAR(45) COMMENT 'IP地址',
    `last_active_at` TIMESTAMP COMMENT '最后活动时间',
    `expires_at` TIMESTAMP COMMENT '过期时间',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_token` (`token`),
    INDEX `idx_expires_at` (`expires_at`)
) COMMENT='用户会话表';
```

#### 3. 好友关系表 (friend)

```sql
CREATE TABLE `friend` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(32) NOT NULL COMMENT '申请者ID',
    `friend_id` VARCHAR(32) NOT NULL COMMENT '被申请者ID',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待审批, 1-已接受, 2-已拒绝, 3-已删除',
    `request_msg` VARCHAR(255) COMMENT '申请留言',
    `request_time` TIMESTAMP COMMENT '申请时间',
    `handle_time` TIMESTAMP COMMENT '处理时间',
    `handle_msg` VARCHAR(255) COMMENT '处理回复',
    `remark` VARCHAR(64) COMMENT '备注名',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY `uk_friend_pair` (`user_id`, `friend_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_friend_id` (`friend_id`),
    INDEX `idx_status` (`status`)
) COMMENT='好友关系表';
```

#### 4. 黑名单表 (blacklist)

```sql
CREATE TABLE `blacklist` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `blocked_user_id` VARCHAR(32) NOT NULL COMMENT '被拉黑的用户ID',
    `reason` VARCHAR(255) COMMENT '拉黑原因',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY `uk_block_pair` (`user_id`, `blocked_user_id`),
    INDEX `idx_user_id` (`user_id`)
) COMMENT='黑名单表';
```

#### 5. 群聊表 (group_chat)

```sql
CREATE TABLE `group_chat` (
    `id` VARCHAR(32) PRIMARY KEY COMMENT '群ID',
    `name` VARCHAR(128) NOT NULL COMMENT '群名称',
    `owner_id` VARCHAR(32) NOT NULL COMMENT '群主ID',
    `avatar` VARCHAR(255) COMMENT '群头像URL',
    `description` VARCHAR(500) COMMENT '群描述',
    `notice` TEXT COMMENT '群公告',
    `member_count` INT DEFAULT 0 COMMENT '成员数',
    `max_members` INT DEFAULT 10000 COMMENT '最大成员数',
    `status` TINYINT DEFAULT 0 COMMENT '群状态: 0-正常, 1-禁用, 2-解散',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `dissolved_at` TIMESTAMP COMMENT '解散时间',
    
    INDEX `idx_owner_id` (`owner_id`),
    INDEX `idx_created_at` (`created_at`)
) COMMENT='群聊表';
```

#### 6. 群成员表 (group_member)

```sql
CREATE TABLE `group_member` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `group_id` VARCHAR(32) NOT NULL COMMENT '群ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `role` TINYINT DEFAULT 2 COMMENT '角色: 1-群主, 2-管理员, 3-普通成员',
    `nickname` VARCHAR(64) COMMENT '群昵称',
    `muted_until` TIMESTAMP COMMENT '禁言截至时间',
    `joined_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `removed_at` TIMESTAMP COMMENT '移除时间',
    
    UNIQUE KEY `uk_group_member` (`group_id`, `user_id`),
    INDEX `idx_group_id` (`group_id`),
    INDEX `idx_user_id` (`user_id`)
) COMMENT='群成员表';
```

#### 7. 群权限表 (group_permission)

```sql
CREATE TABLE `group_permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `group_id` VARCHAR(32) NOT NULL COMMENT '群ID',
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `permission_type` VARCHAR(32) NOT NULL COMMENT '权限类型',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- permission_type: manage_members, manage_info, post_announcement
    UNIQUE KEY `uk_permission` (`group_id`, `user_id`, `permission_type`),
    INDEX `idx_group_id` (`group_id`)
) COMMENT='群权限表';
```

#### 8. ~~消息去重表~~ (已移除 - 前端按时间排序处理重复)

```
不需要消息去重表，改为：
1. 前端生成的消息在本地有dedup_id标记
2. 接收到重复消息时（dedup_id相同），前端自动去重
3. 前端按timestamp排序消息，自动处理顺序问题
```

---

### MongoDB 集合设计

#### 1. 消息集合 (messages)

```javascript
db.createCollection("messages", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["message_id", "conversation_id", "from_user_id", "content", "created_at"],
            properties: {
                _id: { bsonType: "objectId" },
                
                // 消息ID (融云方案: 80Bit, 32进制编码)
                message_id: { 
                    bsonType: "string",
                    description: "BD8U-FCOJ-LDC5-L789"
                },
                
                // 会话信息
                conversation_id: { bsonType: "string" },  // user1_user2 或 group_123
                conversation_type: { bsonType: "int" },  // 0-单聊, 1-群聊
                
                // 用户信息
                from_user_id: { bsonType: "string" },
                to_user_id: { bsonType: "string" },  // 仅单聊使用
                group_id: { bsonType: "string" },    // 仅群聊使用
                
                // 消息内容
                content: { bsonType: "string" },
                message_type: { bsonType: "string" },  // text, image, video, file, audio, recall
                
                // 附加信息 (可选)
                attachment: {
                    bsonType: "object",
                    properties: {
                        url: { bsonType: "string" },
                        size: { bsonType: "int" },
                        mime_type: { bsonType: "string" }
                    }
                },
                
                // 状态信息
                status: { bsonType: "string" },  // sent, delivered, read, recalled
                
                // 撤回信息
                recalled_at: { bsonType: "timestamp" },
                recalled_by: { bsonType: "string" },
                
                // 已读信息 (群聊)
                read_by: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        properties: {
                            user_id: { bsonType: "string" },
                            read_at: { bsonType: "timestamp" }
                        }
                    }
                },
                
                // 时间戳
                created_at: { bsonType: "timestamp" },
                updated_at: { bsonType: "timestamp" }
            }
        }
    }
});

// 索引
db.messages.createIndex({ message_id: 1 }, { unique: true });
db.messages.createIndex({ conversation_id: 1, created_at: 1 });  // 查询会话消息
db.messages.createIndex({ conversation_id: 1, message_id: 1 });  // 游标查询
db.messages.createIndex({ from_user_id: 1, created_at: 1 });
db.messages.createIndex({ group_id: 1, created_at: 1 });
db.messages.createIndex({ created_at: 1 }, { expireAfterSeconds: 31536000 });  // 1年后过期
```

#### 2. 用户会话摘要集合 (chat_sessions)

```javascript
db.createCollection("chat_sessions");

// 结构示例
{
    _id: ObjectId("..."),
    user_id: "user1",
    conversation_id: "user1_user2",
    conversation_type: 0,  // 0-单聊, 1-群聊
    peer_id: "user2",      // 单聊时对方ID
    group_id: "group_123", // 群聊时群ID
    
    // 最后一条消息
    last_message_id: "BD8U-FCOJ-LDC5-L789",
    last_message_content: "你好",
    last_message_type: "text",
    last_message_time: ISODate("2024-05-20T10:00:00Z"),
    last_sender_id: "user2",
    
    // 游标信息
    read_cursor: "BD8U-FCOJ-LDC5-L888",  // 最后读消息ID
    unread_count: 3,
    
    // 会话设置
    is_pinned: false,
    is_muted: false,
    mute_until: ISODate("..."),  // 禁言截至时间
    is_draft: false,
    draft_content: "",
    
    // 时间
    updated_at: ISODate("2024-05-20T10:00:00Z"),
    created_at: ISODate("2024-05-20T08:00:00Z")
}

// 索引
db.chat_sessions.createIndex({ user_id: 1, updated_at: -1 });
db.chat_sessions.createIndex({ user_id: 1, conversation_id: 1 }, { unique: true });
```

#### 3. 消息已读状态集合 (message_read_status)

```javascript
db.createCollection("message_read_status");

{
    _id: ObjectId("..."),
    message_id: "BD8U-FCOJ-LDC5-L789",
    group_id: "group_123",  // 仅群聊
    
    read_by: [
        {
            user_id: "user1",
            read_at: ISODate("2024-05-20T10:00:05Z")
        },
        {
            user_id: "user2",
            read_at: ISODate("2024-05-20T10:00:10Z")
        }
    ],
    
    created_at: ISODate("2024-05-20T10:00:00Z")
}

// 索引
db.message_read_status.createIndex({ message_id: 1 });
db.message_read_status.createIndex({ group_id: 1, message_id: 1 });
```

---

### Redis 缓存层设计

#### 1. 消息ID生成计数器 (核心)

```
Key: msgid:seq:{conversationId}
Type: String (Integer)

用法:
每次发送消息时:
  INCR msgid:seq:user1_user2 → 返回序列号 1, 2, 3, ...
  
  生成消息ID: ${conversationId}#${seq}
  例如: "user1_user2#1", "user1_user2#2"
  
优势:
✓ Redis原子操作，天然并发安全
✓ 保证同一会话内消息序列号递增
✓ 无需数据库操作，性能极高
✓ 支持分布式部署（Redis集群）
✓ 自动支持游标定位（按seq数字比较）

TTL: 永久（实际上用EXPIRE定期清理过期会话）
```

#### 2. 用户在线状态映射表

```
Key: online:user:{userId}
Value: {
    backend_instance: "backend-1:8080",
    connected_at: 1716123456789,
    session_id: "session-xxx"
}
TTL: 30分钟 (心跳续期)
```

#### 3. 分布式路由表

```
Key: router:user:{userId}
Value: {
    user_id: "user1",
    backend_instance: "192.168.1.1:8080",
    connected_at: 1716123456789
}
TTL: 60分钟
```

#### 4. 用户会话摘要

```
Key: chat:session:{userId}
Type: Hash

Fields:
{
    session:{conversationId}:last_msg_id = "user1_user2#5"
    session:{conversationId}:last_msg_time = "1716123456789"
    session:{conversationId}:unread_count = "3"
    session:{conversationId}:read_cursor = "user1_user2#3"
    session:{conversationId}:updated_at = "1716123456789"
}

TTL: 3个月 (活跃会话保留)
```

#### 5. 用户Token缓存

```
Key: token:{token}
Value: {
    user_id: "user1",
    username: "alice",
    expires_at: 1716213456789
}
TTL: token过期时间
```

---

## 📡 RabbitMQ 消息队列设计

### 队列命名规则

```
# 个人消息队列 (单聊离线消息)
chat:user:{userId}
  - 为每个用户创建一个专属队列
  - 消息不丢失（持久化）
  - 自动确认机制
  
# 群聊消息队列
chat:group:{groupId}
  - 所有群成员都监听此队列
  - 消息只投递一次（BROADCAST模式）
```

### 消息格式

```json
{
    "message_id": "BD8U-FCOJ-LDC5-L789",
    "conversation_id": "user1_user2",
    "conversation_type": 0,
    "from_user_id": "user1",
    "to_user_id": "user2",
    "content": "你好",
    "message_type": "text",
    "created_at": 1716123456789,
    "dedup_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 工作流程

```
1. HTTP API 接收消息 → 验证 + 防重
2. 生成消息ID (融云方案)
3. Push to RabbitMQ(chat_user_b)
4. 立即返回给前端 (异步处理)

5. WebSocket监听队列 (消费端)
   - Consumer监听chat_user_b
   - 消费消息 → MongoDB存储
   - 更新Redis游标
   - User B在线? 推送给User B
   - 离线? 消息留在MongoDB等待查询
```

---

## 🌍 分布式部署架构

### 多实例部署方案

```
┌─────────────────────────────────────────────────────────┐
│              Nginx 负载均衡器                            │
│  - HTTP API 轮询分发                                    │
│  - WebSocket 会话保持 (sticky session)                  │
└──────┬──────────────────────────────────────────────┬───┘
       │ HTTP                                          │ WS
   ┌───▼─────────────┐  ┌─────────────────┐  ┌───────▼──────────┐
   │  Backend-1      │  │   Backend-2     │  │   Backend-3      │
   │  8080           │  │   8080          │  │   8080           │
   │                 │  │                 │  │                  │
   │ HTTP Handlers   │  │ HTTP Handlers   │  │ HTTP Handlers    │
   │ (User, Friend,  │  │ (User, Friend,  │  │ (User, Friend,   │
   │  Message, etc)  │  │  Message, etc)  │  │  Message, etc)   │
   │                 │  │                 │  │                  │
   │ WS Listeners    │  │ WS Listeners    │  │ WS Listeners     │
   │ (chat:user:*)   │  │ (chat:user:*)   │  │ (chat:user:*)    │
   │ (chat:group:*)  │  │ (chat:group:*)  │  │ (chat:group:*)   │
   └───┬─────────────┘  └────────┬────────┘  └────────┬─────────┘
       │                         │                    │
       │  ┌──────────────────────┴────────────────────┴─────┐
       │  │                                                  │
       │  ▼                                                  │
    ┌──────────────────────────────────────────────────────┐
    │          Redis (中央缓存 + 路由表)                     │
    │  - online:user:*  (在线状态)                          │
    │  - router:user:*  (路由定位)                          │
    │  - chat:session:* (会话摘要 + 游标)                   │
    │  - token:*        (Token缓存)                         │
    └──────────────────────────────────────────────────────┘
       │                         │                    │
       │  ┌──────────────────────┴────────────────────┴─────┐
       │  │                                                  │
       ▼  ▼                                                  ▼
    ┌──────────────────────────────────────────────────────┐
    │      RabbitMQ Cluster (消息队列)                       │
    │  - chat:user:*   (单聊队列)                           │
    │  - chat:group:*  (群聊队列)                           │
    └──────────────────────────────────────────────────────┘
       │                         │                    │
       │  ┌──────────────────────┴────────────────────┴─────┐
       │  │                                                  │
       ▼  ▼  ▼                                              ▼
    ┌─────────────────────┐          ┌────────────────────┐
    │    MySQL            │          │    MongoDB         │
    │  用户 | 关系 | 权限 │          │  消息 | 会话 | 状态│
    └─────────────────────┘          └────────────────────┘
```

### 用户位置查询流程

```
User A (Backend-1) sends to User B

1. HTTP POST /api/chat/send
   ├─ Backend-1 接收
   ├─ 验证 + 防重
   └─ 生成消息ID

2. 查询路由表
   ├─ Redis 查询 router:user:user_b
   ├─ 返回: Backend-2:8080
   └─ 说明: User B在Backend-2上

3. Push消息到RabbitMQ
   ├─ Queue: chat:user:user_b
   └─ 投递给Backend-2

4. Backend-2 消费消息
   ├─ Consumer监听chat:user:user_b
   ├─ 接收消息 → MongoDB存储
   ├─ 更新Redis游标
   └─ User B在线? WS推送 : 消息留在DB

5. User B上线时
   ├─ 连接到任意Backend
   ├─ 查询游标: read_cursor
   ├─ MongoDB查询新消息
   └─ 一次性推送给User B
```

---

## 📋 API 接口设计

### 1. 用户认证接口

```
POST /api/user/register
Request:
{
    "username": "alice",
    "password": "123456",
    "email": "alice@example.com",
    "nickname": "爱丽丝"
}

Response:
{
    "code": 200,
    "data": {
        "user_id": "user_123",
        "username": "alice",
        "token": "eyJhbGc..."
    }
}
```

```
POST /api/user/login
Request:
{
    "username": "alice",
    "password": "123456"
}

Response:
{
    "code": 200,
    "data": {
        "token": "eyJhbGc...",
        "expires_in": 86400
    }
}
```

### 2. 消息接口

```
POST /api/chat/send
Headers:
{
    "Authorization": "Bearer {token}"
}

Request:
{
    "dedup_id": "550e8400-e29b-41d4-a716-446655440000",
    "conversation_type": 0,  // 0-单聊, 1-群聊
    "to_user_id": "user2",   // 单聊时
    "group_id": "group_123", // 群聊时
    "content": "你好",
    "message_type": "text"
}

Response:
{
    "code": 200,
    "data": {
        "message_id": "BD8U-FCOJ-LDC5-L789",
        "created_at": 1716123456789
    }
}
```

```
GET /api/chat/history
Query:
{
    "conversation_id": "user1_user2",
    "cursor": "BD8U-FCOJ-LDC5-L789",  // 可选，游标分页
    "limit": 20
}

Response:
{
    "code": 200,
    "data": {
        "messages": [...],
        "next_cursor": "BD8U-FCOJ-LDC5-L700"
    }
}
```

### 3. 好友接口

```
POST /api/friend/request
{
    "friend_id": "user2",
    "request_msg": "你好，我想加你为好友"
}

GET /api/friend/list
Query:
{
    "status": 1  // 1-已接受, 其他状态
}

Response:
{
    "code": 200,
    "data": [
        {
            "user_id": "user2",
            "username": "bob",
            "avatar": "...",
            "remark": "工作同事"
        }
    ]
}
```

### 4. 群聊接口

```
POST /api/group/create
{
    "name": "开发小组",
    "member_ids": ["user1", "user2", "user3"]
}

POST /api/group/{groupId}/member/add
{
    "user_id": "user4"
}

POST /api/group/{groupId}/message/recall
{
    "message_id": "BD8U-FCOJ-LDC5-L789"
}
```

---

## 🔌 WebSocket 接口设计

### 连接

```
ws://backend-instance:8080/chat?userId={userId}&token={token}
```

### 消息格式

```javascript
// 发送消息
{
    "action": "send_message",
    "payload": {
        "dedup_id": "...",
        "conversation_type": 0,
        "to_user_id": "user2",
        "content": "你好",
        "message_type": "text"
    }
}

// 接收消息
{
    "action": "message",
    "payload": {
        "message_id": "BD8U-FCOJ-LDC5-L789",
        "from_user_id": "user1",
        "content": "你好",
        "created_at": 1716123456789
    }
}

// 消息已读确认
{
    "action": "message_read",
    "payload": {
        "message_id": "BD8U-FCOJ-LDC5-L789",
        "read_at": 1716123456790
    }
}

// 心跳
{
    "action": "ping",
    "timestamp": 1716123456789
}

// 心跳响应
{
    "action": "pong",
    "timestamp": 1716123456789
}
```

---

## 🔄 完整消息流程

### 一对一聊天流程

```
1️⃣ User A 发送消息
   - Frontend WS send message
   - 同时也通过 HTTP POST 发送备份

2️⃣ Backend 接收 (HTTP Handler)
   - 验证Token
   - 检查dedup_id (防重)
   - 如果重复，直接返回旧消息ID

3️⃣ 生成消息ID
   - MessageIdGenerator 生成融云格式ID
   - 格式: BD8U-FCOJ-LDC5-L789 (80Bit)
   - 保存dedup记录到MySQL

4️⃣ 存储消息
   - MongoDB: 插入消息文档
   - 包含conversation_id, message_id, content等

5️⃣ 查询User B位置
   - Redis查询 router:user:user_b
   - 定位User B所在Backend实例

6️⃣ 推送到消息队列
   - RabbitMQ: chat:user:user_b
   - 消息持久化

7️⃣ Backend 消费消息 (WS Handler)
   - 监听 chat:user:user_b 队列
   - 消费消息
   - 存储MongoDB

8️⃣ 检查User B在线状态
   - Redis查询 online:user:user_b
   - 在线? 直接WS推送
   - 离线? 消息留在MongoDB

9️⃣ User B上线时
   - 建立WS连接
   - 查询Redis游标 read_cursor
   - MongoDB查询 message_id > read_cursor 的消息
   - 一次性推送给User B

🔟 用户确认已读
    - Frontend WS send read_ack
    - 更新Redis: read_cursor = 最新message_id
    - 异步更新MongoDB已读状态
```

---

## 📌 消息ID简化设计

### 新方案：Redis递增序列号

```
消息ID格式: {conversationId}#{sequence}

例如：
- 单聊: "user1_user2#1", "user1_user2#2", "user1_user2#3"
- 群聊: "group_123#1", "group_123#2", "group_123#3"

生成方式：
1. INCR msgid:seq:{conversationId} → 获取sequence
2. 拼接为 message_id = "${conversationId}#${seq}"

优势：
✅ 简洁明了，易于理解
✅ Redis原子操作，高性能
✅ 天然有序（#前面是会话ID，后面是递增序列）
✅ 支持分布式（Redis处理并发）
✅ 游标定位简单（直接比较seq数字）
✅ 分库分表友好（conversationId就是分片key）
✅ 无需复杂的融云算法实现
```

### 为什么不用融云方案？

融云方案的优势（有序+可读）在这里变成劣势：
- 我们不需要跨会话的全局有序
- 只需要**同一会话内有序**
- Redis序列号更简洁、性能更好
- 前端按时间戳排序，可以处理顺序问题

### 前端防重逻辑

```javascript
// 前端维护已发送消息映射
const sentMessages = new Map();  // key: dedup_id, value: message_id

// 发送消息
function sendMessage(content) {
    const dedupId = generateUUID();
    const message = {
        dedup_id: dedupId,
        content: content,
        timestamp: Date.now()
    };
    
    sentMessages.set(dedupId, null);  // 先标记为待确认
    ws.send(JSON.stringify(message));
}

// 接收消息确认
function onMessageAck(response) {
    const { dedup_id, message_id } = response;
    sentMessages.set(dedup_id, message_id);  // 更新为真实ID
}

// 接收重复消息时
function onReceiveMessage(msg) {
    // 检查是否已存在 (通过dedup_id)
    if (sentMessages.has(msg.dedup_id)) {
        console.log('重复消息，忽略');
        return;
    }
    
    // 新消息，按时间戳排序
    messages.push(msg);
    messages.sort((a, b) => a.timestamp - b.timestamp);
}
```

---

## 🛡️ 数据一致性保证

### 防重机制

```
1. 前端生成 dedup_id (UUID)
2. HTTP POST 消息时带上 dedup_id
3. Backend 查询 MySQL message_dedup 表
   - 存在? 返回旧消息ID (幂等)
   - 不存在? 创建新消息，记录dedup_id

4. 24小时后清理过期的dedup_id
```

### 消息顺序保证

```
同一会话内:
- MongoDB按 (conversation_id, created_at) 排序
- message_id包含时间戳，天然有序
- 查询时按message_id排序保证顺序
```

### 离线消息保证

```
1. 消息先Push到RabbitMQ队列
2. 消费端存储到MongoDB
3. User上线时，查询MongoDB获取历史消息
4. Redis游标机制防止重复推送
```

---

## 📈 扩展性和性能

### 水平扩展

```
单个Backend实例支持:
- 10,000+ 并发连接
- 1,000+ 消息/秒吞吐

扩展方案:
- 前端用Nginx负载均衡
- 后端多实例部署
- Redis集群存储路由表
- RabbitMQ集群处理队列
- MongoDB分片存储消息
```

### 分布式部署标准

```
消息数 < 100万 → 单实例足够
消息数 100万-1亿 → MongoDB分片 (按conversation_id)
消息数 > 1亿 → 多个MongoDB分片集群
```

---

## 🎯 技术选型总结

| 层 | 技术 | 原因 |
|---|------|------|
| 前端 | Vue3 + Vite | 现代化, 快速开发 |
| 网关 | Nginx | 负载均衡, WebSocket支持 |
| 后端 | SpringBoot 3 | Java生态, 高性能 |
| WebSocket | Spring WebSocket | 集成度高, 稳定 |
| 用户存储 | MySQL | 关系清晰, 事务支持 |
| 消息存储 | MongoDB | 灵活Schema, 易扩展 |
| 队列 | RabbitMQ | 可靠性高, 持久化 |
| 缓存 | Redis | 高速, 分布式友好 |
| 消息ID | 融云算法 | 有序, 分片友好 |

---

## 📅 开发路线图

### Phase 1: MVP (第1-2周)
- ✅ 用户认证 (JWT + MySQL)
- ✅ WebSocket基础
- ✅ 一对一消息 (MongoDB)
- ✅ 消息防重 (dedup表)
- ✅ 融云消息ID生成

### Phase 2: 关系管理 (第3-4周)
- ✅ 好友申请/管理
- ✅ 黑名单
- ✅ 群聊基础
- ✅ 群成员管理

### Phase 3: 高级功能 (第5-6周)
- ✅ 消息撤回
- ✅ 消息已读状态
- ✅ 群权限管理
- ✅ 聊天记录查询

### Phase 4: 分布式部署 (第7-8周)
- ✅ 多实例部署
- ✅ Redis路由表
- ✅ MongoDB分片
- ✅ RabbitMQ集群

---

## ✅ 架构检查清单

- [x] 系统架构图清晰
- [x] MySQL表设计完整
- [x] MongoDB集合设计完整
- [x] Redis缓存策略明确
- [x] RabbitMQ队列设计合理
- [x] API接口完整
- [x] WebSocket协议定义
- [x] 消息流程详细
- [x] 消息ID设计优雅
- [x] 防重机制完善
- [x] 分布式部署方案可行
- [x] 扩展性和性能考虑

---

本架构设计文档已完整，可以直接进行代码实现！
