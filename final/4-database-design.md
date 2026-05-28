# HappyIM 数据库设计文档

## 总览

| 数据库 | 用途 | 关键集合/表 |
|--------|------|------------|
| MySQL 8.0 | 关系型数据 | user, friend, friend_request, group_chat, group_member, blacklist, id_segment |
| MongoDB 7.0 | 文档型数据 | messages, message_feed, moments, square_posts, moment_notifications, square_notifications, file_feed, user_summary |
| Redis 7 | 缓存/实时数据 | session hash, sessions ZSET, online status, spin counter, leaderboard ZSET, message ID |

---

## 一、MySQL 表结构

### 1.1 user 用户表

```sql
CREATE TABLE user (
    id           BIGINT PRIMARY KEY,
    username     VARCHAR(64) NOT NULL UNIQUE,   -- 用户ID，5位以上字母数字下划线
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,          -- BCrypt
    nickname     VARCHAR(100) NOT NULL,
    avatar_url   VARCHAR(500),
    gender       TINYINT DEFAULT 0,              -- 0=未设 1=男 2=女
    signature    VARCHAR(60),                    -- 个人签名
    description  VARCHAR(250),                   -- 其它说明
    email_verified TINYINT DEFAULT 0,
    status       TINYINT DEFAULT 1,
    last_login_time DATETIME,
    last_login_ip   VARCHAR(50),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 1.2 friend 好友表 (双向存储)

```sql
CREATE TABLE friend (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    friend_id  BIGINT NOT NULL,
    remark     VARCHAR(100),         -- 备注名
    is_starred TINYINT DEFAULT 0,    -- 星标好友
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    UNIQUE KEY uk_pair (user_id, friend_id)
);
```

### 1.3 friend_request 好友申请表

```sql
CREATE TABLE friend_request (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id   BIGINT NOT NULL,
    message      VARCHAR(200),
    status       TINYINT DEFAULT 0,   -- 0=待处理 1=同意 2=拒绝
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    handled_time DATETIME,
    INDEX idx_to_user (to_user_id, status)
);
```

### 1.4 group_chat 群聊表

```sql
CREATE TABLE group_chat (
    id           BIGINT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    avatar_url   VARCHAR(500),
    notice       TEXT,                 -- 群公告
    description  VARCHAR(500),         -- 群简介
    allow_invite TINYINT DEFAULT 1,    -- 允许成员邀请
    owner_id     BIGINT NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 1.5 group_member 群成员表

```sql
CREATE TABLE group_member (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id       BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    role           TINYINT DEFAULT 3,   -- 1=群主 2=管理员 3=普通成员
    group_nickname VARCHAR(100),        -- 群内昵称
    muted_until    DATETIME,
    joined_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member (group_id, user_id)
);
```

### 1.6 id_segment 号段表

```sql
CREATE TABLE id_segment (
    biz_tag    VARCHAR(32) PRIMARY KEY,
    max_id     BIGINT NOT NULL,
    step       INT NOT NULL DEFAULT 1000,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO id_segment VALUES ('user', 20021228, 1000);
INSERT INTO id_segment VALUES ('group', 20021228, 1000);
```

---

## 二、MongoDB 集合设计

### 2.1 messages 消息集合

```json
{
  "_id": ObjectId,
  "messageId": "222E-36KT-25C8-NB3U",  // 80-bit 编码
  "conversationId": "p_10003_10004",
  "conversationType": 0,
  "fromUserId": 10003,
  "content": "你好",
  "messageType": "text",
  "createdAt": 1779777554667,
  "fileName": null,
  "fileSize": null,
  "quoteMessageId": null,
  "quoteContent": null,
  "quoteMessageType": null
}
```

**索引**: `{ messageId: 1 }` (unique), `{ conversationId: 1, createdAt: -1 }`

### 2.2 message_feed 消息 Feed 集合

```json
{
  "_id": ObjectId,
  "userId": 10004,
  "conversationId": "p_10003_10004",
  "messageId": "222E-36KT-25C8-NB3U",
  "createdAt": 1779777554667
}
```

每个会话参与者各写一份 Feed，用于查询该用户的会话列表和消息历史。

**索引**: `{ userId: 1, conversationId: 1, messageId: -1 }`

### 2.3 moments / square_posts 帖子集合

```json
{
  "_id": ObjectId,
  "userId": 10003,
  "nickname": "小深",
  "avatar": "/api/files/avatar/10003",
  "content": "今天天气真好",
  "mediaUrls": "[\"url1\",\"url2\"]",
  "likes": [
    { "userId": 10004, "nickname": "张三", "createdAt": 1716123456789 }
  ],
  "comments": [
    {
      "userId": 10004, "nickname": "张三",
      "content": "确实！",
      "replyToUserId": null,
      "createdAt": 1716123456789
    }
  ],
  "createdAt": 1716123456789
}
```

**为什么嵌入 likes 和 comments？**
- 单文档原子操作 (`$push` / `$pull`)
- 一次查询拿全部数据，无需多次查询
- 朋友圈场景下点赞评论量小，远不到 16MB 限制

### 2.4 moment_notifications / square_notifications

```json
{
  "_id": ObjectId,
  "userId": 10003,
  "fromUserId": 10004,
  "fromNickname": "张三",
  "fromAvatar": "/api/files/avatar/10004",
  "momentId": "507f1f77bcf86cd799439011",
  "type": "like",
  "content": "赞了你的动态",
  "isRead": false,
  "createdAt": 1716123456789
}
```

### 2.5 file_feed 文件 Feed

```json
{
  "_id": ObjectId,
  "userId": 10003,
  "messageId": "222E-36KT-25C8-NB3U",
  "senderId": 10004,
  "senderName": "张三",
  "fileName": "document.pdf",
  "fileSize": 1024000,
  "fileType": "pdf",
  "fileUrl": "/api/files/download/abc123",
  "conversationId": "p_10003_10004",
  "conversationName": "张三",
  "createdAt": 1716123456789
}
```

**索引**: `{ userId: 1, messageId: -1 }` (unique compound), `{ userId: 1, fileType: 1 }`

### 2.6 user_summary 用户摘要

```json
{
  "_id": 10003,            // userId 作为 _id
  "userId": 10003,
  "momentUnread": 3,
  "squareUnread": 0
}
```

页面刷新时直接从摘要读取未读数，`sendNotification` 时 `$inc` 递增，`markRead` 时 `$set 0`。

---

## 三、Redis 数据结构

| Key Pattern | 类型 | 字段/用途 |
|-------------|------|-----------|
| `chat:session:{uid}:{convId}` | Hash | `type, peer_id, peer_name, peer_avatar, last_msg_content, last_msg_type, last_msg_time, unread_count, read_cursor` |
| `chat:sessions:{uid}` | ZSET | 按 lastMsgTime 排序的会话列表 (member=convId, score=timestamp) |
| `online:user:{uid}` | String | 在线状态，60s TTL，30s 心跳刷新 |
| `router:user:{uid}` | String | 用户连接的 WS 服务实例 ID |
| `msgid:spin:{convId}` | String | 消息 ID 毫秒级自旋计数器 |
| `square:leaderboard:{date}` | ZSET | 每日活跃排行榜 (member=userId, score=活跃分) |

### session hash 示例

```
HGETALL chat:session:10003:p_10003_10004
  type           → "0"
  peer_id        → "10004"
  peer_name      → "张三"
  peer_avatar    → "/api/files/avatar/10004"
  last_msg_content → "你好"
  last_msg_type  → "text"
  last_msg_time  → "1779777554667"
  unread_count   → "0"
  read_cursor    → "222E-36KT-25C8-NB3U"
```

---

## 四、MinIO 对象存储

| Bucket | 用途 | 访问方式 |
|--------|------|---------|
| `happyim` | 所有文件 | 后端代理 `/api/files/download/**` |

后端代理方案：前端不直连 MinIO，通过 `/api/files/download/{path}` 下载，支持 Range 请求（视频拖动进度条）。

头像特殊处理：`/api/files/avatar/{userId}` 代理到 MinIO。
