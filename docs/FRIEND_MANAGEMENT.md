# 好友管理模块设计

## 1. 数据库设计

### 1.1 friend_request 表（申请表）

```sql
CREATE TABLE friend_request (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user_id  BIGINT NOT NULL COMMENT '申请人ID',
    to_user_id    BIGINT NOT NULL COMMENT '被申请人ID',
    message       VARCHAR(255) COMMENT '申请留言',
    status        TINYINT DEFAULT 0 COMMENT '0=待处理 1=已同意 2=已拒绝',
    handled_time  DATETIME COMMENT '处理时间',
    created_time  DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_from (from_user_id),
    INDEX idx_to_status (to_user_id, status)
);
```

### 1.2 friend 表（已确认好友）

```sql
CREATE TABLE friend (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT NOT NULL COMMENT '用户ID',
    friend_id     BIGINT NOT NULL COMMENT '好友ID',
    remark        VARCHAR(64) COMMENT '备注名',
    is_starred    TINYINT DEFAULT 0 COMMENT '是否星标',
    created_time  DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_friend_pair (user_id, friend_id),
    INDEX idx_user_id (user_id)
);
```

### 1.3 blacklist 表（黑名单）

```sql
CREATE TABLE blacklist (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '拉黑者ID',
    blocked_user_id BIGINT NOT NULL COMMENT '被拉黑者ID',
    reason          VARCHAR(255),
    created_time    DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_block_pair (user_id, blocked_user_id),
    INDEX idx_user_id (user_id)
);
```

---

## 2. 业务规则

### 好友关系说明

- 同意申请时**双向写入两条**：`(A,B)` 和 `(B,A)`
- 查自己的好友列表只需查一条：`WHERE user_id = me`
- 发消息时校验双向都存在：两个方向都是好友才能发
- 删除好友时**只删自己那条**：A 删 B 只删 `(A,B)`，B 那边不知道

### 2.1 好友申请

```
A 向 B 发送申请:
  1. A ≠ B（不能给自己发）
  2. SELECT * FROM friend WHERE user_id=B AND friend_id=A → B 已经有 A 为好友？拒绝
  3. SELECT * FROM friend_request WHERE from=A AND to=B AND status=0 → 已有待处理申请？UPDATE 覆盖 message + created_time
  4. SELECT * FROM blacklist WHERE user_id=B AND blocked_user_id=A → 被 B 拉黑了？拒绝
  5. INSERT INTO friend_request (from=A, to=B, status=0)
```

### 2.2 处理申请

```
B 同意 A 的申请:
  1. 查 friend_request → 必须是 to=B, status=0
  2. UPDATE friend_request SET status=1, handled_time=NOW()
  3. INSERT INTO friend (user_id=A, friend_id=B)   ← 双向写入
  4. INSERT INTO friend (user_id=B, friend_id=A)

B 拒绝 A 的申请:
  1. UPDATE friend_request SET status=2, handled_time=NOW()
```

### 2.3 删除好友

```
B 删除好友 A:
  1. DELETE FROM friend WHERE user_id=B AND friend_id=A   ← 只删 B 自己的记录
  2. A 那边不受影响，A 不知道 B 删了他
```

### 2.4 拉黑

```
B 拉黑 A:
  1. INSERT INTO blacklist (user_id=B, blocked_user_id=A)
  2. 不删除 friend 记录，拉黑不影响好友关系
  3. 效果：A 无法给 B 发消息，看不到 B 的动态

B 取消拉黑 A:
  1. DELETE FROM blacklist WHERE user_id=B AND blocked_user_id=A
```

---

## 3. API 接口

### 3.1 搜索用户

```
GET /api/users/search?keyword=xxx

Response:
{
  "code": 0,
  "data": [
    {
      "userId": 10001,
      "username": "zhangsan",
      "nickname": "张三",
      "avatarUrl": null
    }
  ]
}
```

### 3.2 发送好友申请

```
POST /api/friends/request
Authorization: Bearer {token}

Request:
{
  "toUserId": 10001,
  "message": "你好，我是李四"
}

Response (200):
{ "code": 0, "message": "好友申请已发送" }

Response (400):
{
  "code": 30001, "message": "对方已是你的好友"
  "code": 30003, "message": "对方已将你拉黑"
  "code": 30004, "message": "不能对自己操作"
}
```

### 3.3 收到的好友申请列表

```
GET /api/friends/requests
Authorization: Bearer {token}

Response:
{
  "code": 0,
  "data": [
    {
      "id": 5001,
      "fromUserId": 10001,
      "fromUsername": "zhangsan",
      "fromNickname": "张三",
      "fromAvatarUrl": null,
      "message": "你好，我是张三",
      "status": 0,
      "createdTime": "2026-05-25T20:00:00"
    }
  ]
}
```

### 3.4 同意好友申请

```
POST /api/friends/requests/{requestId}/accept
Authorization: Bearer {token}

Response:
{ "code": 0, "message": "已添加为好友" }
```

### 3.5 拒绝好友申请

```
POST /api/friends/requests/{requestId}/reject
Authorization: Bearer {token}

Response:
{ "code": 0, "message": "已拒绝" }
```

### 3.6 好友列表

```
GET /api/friends
Authorization: Bearer {token}

Response:
{
  "code": 0,
  "data": [
    {
      "userId": 10001,
      "username": "zhangsan",
      "nickname": "张三",
      "avatarUrl": null,
      "remark": "老张",
      "isStarred": false
    }
  ]
}
```

### 3.7 删除好友

```
DELETE /api/friends/{userId}
Authorization: Bearer {token}

Response:
{ "code": 0, "message": "已删除好友" }
```

### 3.8 星标/取消星标

```
PUT /api/friends/{userId}/star
Authorization: Bearer {token}

Request:
{ "starred": true }

Response:
{ "code": 0, "message": "已设置星标" }
```

### 3.9 拉黑

```
POST /api/friends/{userId}/block
Authorization: Bearer {token}

Response:
{ "code": 0, "message": "已拉黑" }
```

### 3.10 取消拉黑

```
POST /api/friends/{userId}/unblock
Authorization: Bearer {token}

Response:
{ "code": 0, "message": "已取消拉黑" }
```

### 3.11 黑名单列表

```
GET /api/friends/blacklist
Authorization: Bearer {token}

Response:
{
  "code": 0,
  "data": [
    {
      "userId": 10001,
      "username": "zhangsan",
      "nickname": "张三",
      "avatarUrl": null,
      "blockedTime": "2026-05-25T20:00:00"
    }
  ]
}
```

---

## 4. 前端页面设计

参考 `frontend reference/frontend/src/pages/ContactsPage.vue` 的布局和交互模式。

### 4.1 整体三栏布局

```
┌──────┬──────────────────┬──────────────────────────┐
│ 导航 │   好友列表 250px  │     内容面板（右栏）       │
│ 60px │                  │                          │
├──────┼──────────────────┼──────────────────────────┤
│      │  🔍 搜索 [+加]   │  根据选中状态切换:         │
│  💬  │                  │                          │
│  👥  │  [新的朋友] 红点  │  - 默认: 空白占位         │
│      │                  │  - 新的朋友: 申请列表     │
│      │  ── 我的好友 ──  │  - 好友详情: 信息卡片     │
│      │  [头像] 张三      │  - 查找用户: 搜索结果     │
│      │  [头像] 李四      │                          │
│      │  ...             │                          │
│      │                  │                          │
│      │  ── 我的群聊 ──  │                          │
│      │  (本期暂不实现)   │                          │
└──────┴──────────────────┴──────────────────────────┘
```

### 4.2 页面路由

| URL | 左栏 | 中间栏 | 右栏 |
|-----|------|--------|------|
| `/contacts` | 通讯录高亮 | 好友列表 | 空白占位 |
| `/contacts/:userId` | 通讯录高亮 | 好友列表（选中该用户） | 好友详情 |

### 4.3 中间栏：好友列表

**搜索栏**（顶部，60px高）：

```
┌─────────────────────────┐
│  [🔍 搜索好友...]  [+]  │
└─────────────────────────┘
```

- 默认模式：搜索框过滤好友列表
- 点击 `[+]` 切换到"查找用户"模式，搜索框变成"查找用户"，右侧出现"取消"按钮
- 在查找模式下输入关键字 → 调用 `GET /api/users/search?keyword=xxx` → 右栏显示搜索结果

**"新的朋友" 入口**：

```
┌─────────────────────────┐
│  [头像图标]              │
│  新的朋友           (3)  │  ← el-badge 红点数字
└─────────────────────────┘
```

- 红点显示待处理申请数（`GET /api/friends/requests` 中 status=0 的数量）
- 点击 → 右栏切换到"新的朋友"面板，清除红点

**好友列表**：

```
┌─────────────────────────┐
│  [头像] 老张  (备注名)   │  ← displayName: remark || nickname
│  [头像] 李四            │
│  [头像] 王五            │
└─────────────────────────┘
```

- 调用 `GET /api/friends` 获取列表
- 每项显示头像 + displayName（备注优先，无备注显示昵称）
- 点击 → 右栏展示该好友的详情
- 高亮当前选中项

---

### 4.4 右栏 1：新的朋友（申请列表）

```
┌──────────────────────────────────┐
│  新的朋友                         │
│ ──────────────────────────────── │
│                                  │
│  [头像] 张三                      │
│  请求备注：你好，想加你为好友        │
│  请求时间：2026-05-25 20:00       │
│  [接受]  |  [删除]               │
│                                  │
│ ──────────────────────────────── │
│                                  │
│  [头像] 王五                      │
│  ✓ 已处理       [删除]            │
│                                  │
└──────────────────────────────────┘
```

- 调用 `GET /api/friends/requests` 获取列表
- **待处理**（status=0）：显示 [接受] 和 [删除] 按钮
- **已处理**（status=1/2）：显示"已处理"标签 + [删除] 按钮
- 点击 [接受] → 弹出同意对话框
- 点击 [删除] → 调 `DELETE /api/friends/requests/{id}` 删除该申请记录

**同意对话框**（el-dialog）：

```
┌──────────────────────────┐
│  通过朋友验证             │
├──────────────────────────┤
│  备注名：[_________]     │
│  标签：  [___] (可选)    │
│          [取消] [确定]   │
└──────────────────────────┘
```

- 确定 → 调 `POST /api/friends/requests/{id}/accept`

---

### 4.5 右栏 2：好友详情

```
┌──────────────────────────────────┐
│  ┌────────────────────────────┐  │
│  │ [大头像]  张三               │  │
│  │           昵称：张三         │  │
│  │           账号：zhangsan    │  │
│  └────────────────────────────┘  │
│ ──────────────────────────────── │
│  备注名    [___老张___]           │
│  个性签名   这个人很懒，什么都没写    │
│  邮箱      zhangsan@example.com   │
│  建号时间   2026-05-20            │
│ ──────────────────────────────── │
│                                  │
│    [发消息]        [删除好友]      │
│                                  │
└──────────────────────────────────┘
```

- 好友详情调 `GET /api/users/{userId}` 获取用户公开信息
- [发消息] → 跳转 `/chat/p_{myId}_{friendId}`
- [删除好友] → 二次确认弹窗 → 调 `DELETE /api/friends/{userId}`

**删除确认弹窗**：

```
┌──────────────────────────┐
│  删除好友                 │
├──────────────────────────┤
│  确定要删除好友"张三"吗？   │
│  删除后将无法撤销。        │
│          [取消] [确定删除] │
└──────────────────────────┘
```

---

### 4.6 右栏 3：查找用户（搜索模式）

搜索栏点击 `[+]` 后切换到查找模式：

```
┌──────────────────────────────────┐
│  查找用户                         │
│ ──────────────────────────────── │
│                                  │
│  [头像] 张三                      │
│  TEL: - ｜ email：zhan@example   │
│                            [添加] │
│                                  │
│ ──────────────────────────────── │
│                                  │
│  [头像] 李四                      │
│  TEL: - ｜ email：li@example     │
│                            [添加] │
└──────────────────────────────────┘
```

- 输入关键字 → 调 `GET /api/users/search?keyword=xxx`
- 已经加为好友的用户不显示 [添加] 按钮，或显示"已是好友"
- 点击 [添加] → 弹出申请对话框

**申请对话框**（el-dialog）：

```
┌──────────────────────────┐
│  请求添加好友             │
├──────────────────────────┤
│  备注名：[_________]     │
│  请求信息：              │
│  ┌────────────────────┐  │
│  │ 你好，我是xxx       │  │
│  └────────────────────┘  │
│          [取消] [确定]   │
└──────────────────────────┘
```

- 确定 → 调 `POST /api/friends/request`

---

### 4.7 组件结构

```
ContactsPage.vue
├── 左侧好友列表 (class="friend-list")
│   ├── 搜索栏 (search-bar)
│   │   ├── el-input (搜索好友 / 查找用户)
│   │   └── [+]/[取消] 切换按钮
│   ├── "新的朋友" 入口 (el-badge)
│   └── 好友列表 (v-for)
│
├── 右栏 (class="friend-window")
│   ├── 新的朋友面板 (v-if activeType === 'newFriend')
│   │   └── friend-request-item (v-for)
│   ├── 好友详情面板 (v-if activeType === 'friend')
│   │   └── profile-card + info-card + action-bar
│   └── 查找用户面板 (v-if searchMore)
│       └── friend-request-item (v-for)
│
├── 同意对话框 (el-dialog)
├── 申请对话框 (el-dialog)
└── 删除确认 (el-message-box)
```

---

## 5. 新增错误码

| code | 说明 |
|------|------|
| 30001 | 对方已是你的好友 |
| 30003 | 对方已将你拉黑 |
| 30004 | 不能对自己操作 |

