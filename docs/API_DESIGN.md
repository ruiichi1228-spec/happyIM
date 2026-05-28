# HappyIM API 接口设计

## 统一规范

### 基础 URL

```
开发环境: http://localhost:8080
生产环境: https://api.happyim.com
```

### 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": { }
}
```

### 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| 10001 | 参数错误 |
| 10002 | 未登录 / Token 过期 |
| 10003 | 权限不足 |
| 10004 | 资源不存在 |
| 10005 | 重复操作 |
| 10006 | 操作被拒绝（被拉黑、不是好友等） |
| 20001 | 用户名已存在 |
| 20002 | 邮箱已注册 |
| 20003 | 密码错误 |
| 20004 | 好友申请已过期 |
| 50001 | 服务器内部错误 |

### 分页格式

```
GET /api/xxx?cursor={lastId}&limit=20

请求: cursor 为空表示从最新开始
响应: {
  "list": [...],
  "next_cursor": "xxx",   // 下一页游标，为空表示到底了
  "has_more": true
}
```

---

## 一、认证模块

### 1.1 注册

```
POST /api/auth/register

Request:
{
  "username": "zhangsan",       // 3-32字符，字母数字下划线
  "password": "Abc123456",      // 8-64字符
  "email": "zhangsan@example.com",
  "nickname": "张三"             // 1-32字符
}

Response:
{
  "code": 0,
  "data": {
    "user_id": 10000,
    "username": "zhangsan",
    "nickname": "张三",
    "access_token": "eyJhbG...",
    "refresh_token": "eyJhbG...",
    "expires_in": 900           // access_token 15分钟过期
  }
}
```

### 1.2 登录

```
POST /api/auth/login

Request:
{
  "account": "zhangsan",        // 用户名或邮箱
  "password": "Abc123456",
  "device": "web"               // web / android / ios
}

Response: 同注册，返回 token 对
```

### 1.3 刷新 Token

```
POST /api/auth/refresh

Request:
{
  "refresh_token": "eyJhbG...",
  "device": "web"
}

Response:
{
  "code": 0,
  "data": {
    "access_token": "eyJhbG...",   // 新的
    "refresh_token": "eyJhbG...",  // 新的（旧 refresh_token 立即失效）
    "expires_in": 900
  }
}
```

### 1.4 登出

```
POST /api/auth/logout
Authorization: Bearer {access_token}

Request:
{
  "refresh_token": "eyJhbG..."
}

Response: { "code": 0 }
```

---

## 二、JWT Token 设计

### Access Token

```
Header: { "alg": "HS256", "typ": "JWT" }
Payload: {
  "sub": "10000",          // user_id
  "username": "zhangsan",
  "device": "web",         // web / android / ios
  "iat": 1716123456,
  "exp": 1716124356        // 15分钟
}
```

### Refresh Token

```
Payload: {
  "sub": "10000",
  "device": "web",
  "jti": "uuid",           // 唯一标识，用于撤销
  "iat": 1716123456,
  "exp": 1716728256        // 7天
}

存储在 MySQL user_session 表，每设备一条记录。
多端登录 = 每个 device 各有一个独立的 refresh_token。
```

### 前端调度策略

```
请求拦截器:
  每次 HTTP 请求前:
    if access_token 剩余时间 < 5分钟:
      用 refresh_token 提前续期
      拿到新 token 对后，再发原请求

  如果返回 10002（token过期）:
    用 refresh_token 续期 → 重试原请求
    如果续期也失败 → 跳转登录页

WebSocket:
  连接时在 query 参数带上 access_token
  连接建立后，token 过期不影响已建立的连接
  断开重连前，先刷新 token 再重连
```

---

## 三、用户模块

### 3.1 获取自己的信息

```
GET /api/users/me
Authorization: Bearer {token}

Response:
{
  "code": 0,
  "data": {
    "user_id": 10000,
    "username": "zhangsan",
    "nickname": "张三",
    "email": "zhangsan@example.com",
    "avatar": "https://minio.happyim.com/avatars/10000.jpg",
    "signature": "这个人很懒，什么都没写",
    "gender": 0,
    "created_at": 1716123456789
  }
}
```

### 3.2 修改自己的信息

```
PUT /api/users/me

Request:
{
  "nickname": "张三丰",
  "signature": "道可道，非常道",
  "gender": 1
}
```

### 3.3 搜索用户

```
GET /api/users/search?q={keyword}

搜索用户名或邮箱，模糊匹配

Response:
{
  "code": 0,
  "data": {
    "list": [
      {
        "user_id": 10001,
        "username": "lisi",
        "nickname": "李四",
        "avatar": "..."
      }
    ]
  }
}
```

### 3.4 获取用户公开信息

```
GET /api/users/{userId}

Response:
{
  "code": 0,
  "data": {
    "user_id": 10001,
    "username": "lisi",
    "nickname": "李四",
    "avatar": "...",
    "signature": "...",
    "gender": 1
  }
}
```

---

## 四、好友模块

### 好友关系说明

微信式**双向好友**：
1. A 发送好友申请给 B
2. B 同意 → 双向好友关系建立
3. A 删除 B → A 的好友列表移除 B，B 的好友列表仍有 A
4. B 再删 A → 双向都不再是好友

实际上更简化：任何一方删除好友，双方都解除关系。这个可以再讨论，我先按微信逻辑来。

### 4.1 发送好友申请

```
POST /api/friends/request

Request:
{
  "user_id": 10001,
  "message": "你好，我是张三"
}

校验:
  - 不能给自己发
  - 不能重复发送待处理的申请
  - 已经不是好友才能发
  - 对方没拉黑自己
```

### 4.2 收到的好友申请列表

```
GET /api/friends/requests

Response:
{
  "code": 0,
  "data": {
    "list": [
      {
        "id": 5001,
        "from_user": {
          "user_id": 10001,
          "username": "lisi",
          "nickname": "李四",
          "avatar": "..."
        },
        "message": "你好，我是李四",
        "status": 0,              // 0-待处理
        "created_at": 1716123456789
      }
    ]
  }
}
```

### 4.3 同意 / 拒绝好友申请

```
POST /api/friends/requests/{requestId}/accept
POST /api/friends/requests/{requestId}/reject
```

### 4.4 好友列表

```
GET /api/friends

Response:
{
  "code": 0,
  "data": {
    "list": [
      {
        "user_id": 10001,
        "username": "lisi",
        "nickname": "李四",
        "avatar": "...",
        "signature": "...",
        "remark": "李老四",        // 我给他备注的名字
        "is_starred": true,
        "first_letter": "L"        // 用于前端 A-Z 分组
      }
    ]
  }
}
```

### 4.5 删除好友

```
DELETE /api/friends/{userId}
```

### 4.6 星标 / 取消星标

```
PUT /api/friends/{userId}/star    → { "starred": true }
PUT /api/friends/{userId}/unstar  → { "starred": false }
```

### 4.7 拉黑 / 取消拉黑

```
POST /api/friends/{userId}/block
Response: 拉黑后自动解除好友关系（如果有的话）

POST /api/friends/{userId}/unblock
```

### 4.8 黑名单列表

```
GET /api/friends/blacklist
```

---

## 五、会话模块

### 会话创建时机

发起第一条聊天消息时，后端自动创建会话。会话表存 MySQL，Redis 摘要同步维护。

```sql
CREATE TABLE `conversation` (
    `id`         VARCHAR(64) PRIMARY KEY COMMENT '会话ID: p_10000_10001 / g_10000 / b_10000_bot / s_10000',
    `type`       TINYINT NOT NULL     COMMENT '0-私聊 1-群聊 2-机器人 3-通知',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='会话表';
```

### 5.1 获取会话列表

直接返回 Redis 摘要数据，不查 MySQL。

```
GET /api/conversations

Response:
{
  "code": 0,
  "data": {
    "pinned": [
      {
        "conversation_id": "p_10000_10002",
        "type": 0,
        "peer_id": "10002",
        "peer_name": "王五",
        "peer_avatar": "...",
        "last_msg_content": "明天几点开会？",
        "last_msg_type": "text",
        "last_msg_time": 1716123456789,
        "last_sender_id": "10002",
        "unread_count": 3,
        "is_muted": false
      }
    ],
    "recent": [
      {
        "conversation_id": "p_10000_10001",
        "type": 0,
        "peer_id": "10001",
        "peer_name": "李四",
        "peer_avatar": "...",
        "last_msg_content": "好的，再见",
        "last_msg_type": "text",
        "last_msg_time": 1716123456780,
        "last_sender_id": "10000",
        "unread_count": 0,
        "is_muted": false
      }
    ]
  }
}
```

---

## 六、消息模块

### 6.1 获取消息历史

游标分页，每次 20 条，从最新开始往回拉。

```
GET /api/conversations/{conversationId}/messages?limit=20

cursor 为空 → 取最新 20 条
cursor = "BD8U-xxx" → 取这个 ID 之前的 20 条

请求示例:
GET /api/conversations/p_10000_10001/messages?limit=20
GET /api/conversations/p_10000_10001/messages?cursor=BD8U-DE7K-L0M3-N2P4&limit=20

Response:
{
  "code": 0,
  "data": {
    "list": [
      {
        "message_id": "BD8U-FCOJ-LDC5-L789",
        "conversation_id": "p_10000_10001",
        "from_user_id": "10001",
        "content": "你好",
        "message_type": "text",
        "status": "read",
        "created_at": 1716123456789
      },
      {
        "message_id": "BD8U-DE7K-L0M3-N2P4",
        "conversation_id": "p_10000_10001",
        "from_user_id": "10000",
        "content": "你好，在吗",
        "message_type": "text",
        "status": "read",
        "created_at": 1716123456780
      }
    ],
    "next_cursor": "BD8U-DE7K-L0M3-N2P4",
    "has_more": true
  }
}
```

### 6.2 发送消息（HTTP 备用通道）

```
POST /api/conversations/{conversationId}/messages

Request:
{
  "content": "你好",
  "message_type": "text"
}

Response:
{
  "code": 0,
  "data": {
    "message_id": "BD8U-FCOJ-LDC5-L789",
    "created_at": 1716123456789
  }
}
```

### 6.3 标记会话已读

```
PUT /api/conversations/{conversationId}/read

Request:
{
  "read_cursor": "BD8U-FCOJ-LDC5-L789"   // 读到的最后一条消息 ID
}

后端: HINCRBY unread_count = 0, HSET read_cursor
```

---

## 七、群聊模块

### 7.1 创建群聊

```
POST /api/groups

Request:
{
  "name": "开发小组",
  "member_ids": [10001, 10002, 10003]
}

Response:
{
  "code": 0,
  "data": {
    "group_id": 10000,
    "conversation_id": "g_10000",
    "name": "开发小组",
    "owner_id": 10000,
    "member_count": 4,
    "created_at": 1716123456789
  }
}
```

### 7.2 群列表

```
GET /api/groups

Response:
{
  "list": [
    {
      "group_id": 10000,
      "name": "开发小组",
      "avatar": "...",
      "owner_id": 10000,
      "member_count": 4,
      "my_role": 1          // 1-群主 2-管理员 3-成员
    }
  ]
}
```

### 7.3 群详情

```
GET /api/groups/{groupId}
```

### 7.4 群成员管理

```
POST /api/groups/{groupId}/members    → { "user_ids": [10004] }
DELETE /api/groups/{groupId}/members/{userId}
```

### 7.5 退出 / 解散群

```
POST /api/groups/{groupId}/leave      // 退出
POST /api/groups/{groupId}/dissolve   // 解散（群主）
```

---

## 八、文件上传（MinIO）

### 预签名 URL 方式（推荐）

前端直传 MinIO，后端只负责签名。

```
POST /api/files/upload-url

Request:
{
  "filename": "photo.jpg",
  "content_type": "image/jpeg",
  "file_size": 204800
}

Response:
{
  "code": 0,
  "data": {
    "upload_url": "https://minio.happyim.com/chat/xxx?sign=...",
    "file_url": "https://minio.happyim.com/chat/BD8U-photo.jpg",
    "expires_in": 300       // 上传 URL 5分钟有效
  }
}
```

流程：前端获取签名 URL → 直传 MinIO → 拿到 file_url → 作为消息 content 发送

---

## 九、WebSocket 协议

### 连接

```
ws://localhost:8080/ws?token={access_token}&device=web
```

连接建立时验证 access_token，建立后不因 token 过期而断开。

### 消息格式

所有消息遵循统一格式：

```json
{
  "action": "xxx",
  "data": { }
}
```

### 服务端 → 客户端

```json
// 新消息
{
  "action": "message",
  "data": {
    "message_id": "BD8U-FCOJ-LDC5-L789",
    "conversation_id": "p_10000_10001",
    "from_user_id": 10001,
    "content": "你好",
    "message_type": "text",
    "created_at": 1716123456789
  }
}

// 会话摘要更新（新消息来时推送）
{
  "action": "session_update",
  "data": {
    "conversation_id": "p_10000_10001",
    "last_msg_content": "你好",
    "last_msg_time": 1716123456789,
    "unread_count": 3
  }
}

// 消息被撤回
{
  "action": "message_recall",
  "data": {
    "message_id": "BD8U-FCOJ-LDC5-L789",
    "conversation_id": "p_10000_10001"
  }
}

// 心跳
{ "action": "pong" }
```

### 客户端 → 服务端

```json
// 发送消息
{
  "action": "send_message",
  "data": {
    "conversation_id": "p_10000_10001",
    "content": "你好",
    "message_type": "text"
  }
}

// 发送消息的响应
{
  "action": "send_ack",
  "data": {
    "local_id": "temp-xxx",       // 客户端的临时 ID
    "message_id": "BD8U-FCOJ-LDC5-L789",
    "created_at": 1716123456789,
    "status": "ok"
  }
}

// 标记已读
{
  "action": "mark_read",
  "data": {
    "conversation_id": "p_10000_10001",
    "read_cursor": "BD8U-FCOJ-LDC5-L789"
  }
}

// 心跳（每 30 秒）
{ "action": "ping" }
```

---

## 十、完整消息流程

```
1. 用户 10000 在聊天窗口输入 "你好" 发给 10001
   
2. 前端通过 WebSocket 发送:
   { "action": "send_message", "data": { "conversation_id": "p_10000_10001", ... } }
   同时本地立即显示消息气泡（状态: 发送中），带上临时 local_id

3. 后端:
   - 生成 messageId (80-bit)
   - MongoDB insertOne(message)
   - 更新 Redis 摘要 (两个用户各自的 Hash + ZSET)
   - 如果 receiver 在线 → WS 推送 "message" 事件
   - 推送 "session_update" 给 receiver
   - 返回 "send_ack" 给 sender（把 local_id 和正式 message_id 对应起来）

4. 前端收到 send_ack:
   - 根据 local_id 找到本地消息，更新状态为 "已发送" ✓
   - 记录正式 message_id

5. 10001 前端收到 "message" 事件:
   - 如果当前正在聊 p_10000_10001 → 直接渲染
   - 如果不在 → 不渲染，等进入聊天时 HTTP 拉取

6. 10001 点击会话:
   - GET /api/conversations/p_10000_10001/messages?limit=20
   - 渲染最近 20 条
   - 下拉加载更多

7. 10001 看到消息后，前端自动标记已读:
   - WS send { "action": "mark_read", "data": { "conversation_id": "...", "read_cursor": "..." } }
   - 后端更新 Redis: unread_count = 0, read_cursor = xxx
```

---

## 十一、移动端预留

### 平台标识

所有接口的 `device` 字段：`web` / `android` / `ios`。后端根据 device 类型可以做差异化处理（如 Web 支持多标签页，手机支持通知推送）。

### 推送预留

```
Phone 端登录时:
  注册 device_token (APNs / FCM)
  
后续消息推送:
  if WS 在线 → WS 实时推送
  else → 手机系统推送 (APNs / FCM)
```

Web 端不需要推送，只通过 WebSocket。

---

## 十二、文件上传 API

### 头像上传

```
POST /api/users/me/avatar

Content-Type: multipart/form-data
file: {binary}

后端:
  1. 上传到 MinIO (avatars/{userId}.jpg)
  2. 更新 MySQL user.avatar 字段
  3. 更新所有相关 Redis 摘要中的 peer_avatar（异步批量）

Response:
{
  "code": 0,
  "data": {
    "avatar": "https://minio.happyim.com/avatars/10000.jpg"
  }
}
```

### 聊天文件上传

```
POST /api/files/upload

Content-Type: multipart/form-data
file: {binary}

后端:
  1. 上传到 MinIO (chat_files/{messageId}.{ext})
  2. 返回文件 URL

Response:
{
  "code": 0,
  "data": {
    "file_url": "https://minio.happyim.com/chat_files/BD8U-xxx.jpg",
    "file_size": 204800,
    "mime_type": "image/jpeg"
  }
}
```

前端拿到 file_url 后，作为消息 content 发送（message_type: image / file）。
