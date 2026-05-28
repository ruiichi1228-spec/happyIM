# 群聊管理模块设计

## 1. 概述

在通讯录页面（250px 左侧列表）顶部搜索框右侧有一个 `[+]` 按钮。点击后弹出联系人选择器，前端根据选中人数自动判断：

- **选 1 人** → 创建私聊会话（`p_{uid1}_{uid2}`），直接跳转聊天页
- **选多人** → 弹出创建群聊对话框，输入群名称必填，头像可选，确认后创建群

---

## 2. 数据库设计

### 2.1 group_chat 表

```sql
CREATE TABLE group_chat (
    id              BIGINT PRIMARY KEY COMMENT '群ID，号段生成器分配',
    name            VARCHAR(128) NOT NULL COMMENT '群名称',
    owner_id        BIGINT NOT NULL COMMENT '群主ID',
    avatar_url      VARCHAR(500) COMMENT '群头像',
    description     VARCHAR(500) COMMENT '群简介',
    notice          TEXT COMMENT '群公告',
    member_count    INT DEFAULT 0 COMMENT '当前成员数',
    max_members     INT DEFAULT 500 COMMENT '人数上限',
    allow_invite    TINYINT DEFAULT 1 COMMENT '是否允许普通成员邀请: 0=仅管理员 1=所有人',
    status          TINYINT DEFAULT 0 COMMENT '0=正常 1=已解散',
    created_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_owner (owner_id)
);
```

### 2.2 group_member 表

```sql
CREATE TABLE group_member (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id        BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    role            TINYINT DEFAULT 3 COMMENT '1=群主 2=管理员 3=普通成员',
    group_nickname  VARCHAR(64) COMMENT '群内昵称',
    muted_until     DATETIME COMMENT '禁言截止时间',
    joined_time     DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_group_user (group_id, user_id),
    INDEX idx_user (user_id)
);
```

### 2.3 conversation 表（新增，本期要做）

```sql
CREATE TABLE conversation (
    id              VARCHAR(64) PRIMARY KEY COMMENT '会话ID: p_10001_10002 / g_10000',
    type            TINYINT NOT NULL COMMENT '0=私聊 1=群聊',
    created_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

会话创建时机：
- 私聊：两个人第一次发消息时自动创建，ID 格式 `p_{uid1}_{uid2}`（两个 uid 字典序小在前）
- 群聊：创建群的同时创建，ID 格式 `g_{groupId}`

### 2.4 id_segment 表（追加）

```sql
INSERT INTO id_segment (biz_tag, max_id, step) VALUES ('group', 20021228, 1000);
```

---

## 3. 群 ID

复用 `IdGenerator`，`biz_tag = "group"`，纯数字递增，与用户 ID 共享号段机制。

---

## 4. 权限模型

| 操作 | 群主 | 管理员 | 普通成员 |
|------|------|--------|----------|
| 修改群名称/头像/简介 | ✓ | ✓ | |
| 编辑群公告 | ✓ | ✓ | |
| 邀请成员 | ✓ | ✓ | ✓(allow_invite=1时) |
| 移除成员 | ✓ | ✓ | |
| 设置/取消管理员 | ✓ | | |
| 解散群 | ✓ | | |
| 退出群 | | ✓ | ✓ |
| 修改群配置(allow_invite等) | ✓ | | |

---

## 5. API 接口

### 5.1 创建群聊

```
POST /api/groups

Request:
{
  "name": "开发小组",           // 必填
  "memberIds": [10002, 10003]  // 必填，初始成员（群主自动加入，不需要包含自己）
}

逻辑:
  1. IdGenerator.nextGroupId() 生成群ID
  2. INSERT INTO group_chat
  3. INSERT INTO group_member (owner + members)，群主 role=1，成员 role=3
  4. UPDATE group_chat SET member_count = N
  5. INSERT INTO conversation (id="g_{groupId}", type=1)
  6. 初始化 Redis 群会话摘要

Response:
{
  "code": 0,
  "data": {
    "groupId": 10000,
    "name": "开发小组",
    "ownerId": 10001,
    "memberCount": 3,
    "conversationId": "g_10000",
    "createdTime": "2026-05-26T20:00:00"
  }
}
```

### 5.2 我的群列表

```
GET /api/groups

Response:
{
  "code": 0,
  "data": [
    {
      "groupId": 10000,
      "name": "开发小组",
      "avatarUrl": "/api/files/avatar/group/10000",
      "ownerId": 10001,
      "memberCount": 5,
      "myRole": 1
    }
  ]
}
```

### 5.3 群详情

```
GET /api/groups/{groupId}

Response:
{
  "code": 0,
  "data": {
    "groupId": 10000,
    "name": "开发小组",
    "avatarUrl": "...",
    "description": "...",
    "notice": "...",
    "ownerId": 10001,
    "memberCount": 5,
    "maxMembers": 500,
    "allowInvite": true,
    "myRole": 1,
    "members": [
      { "userId": 10001, "username": "zhangsan", "nickname": "张三", "avatarUrl": "...", "role": 1 },
      { "userId": 10002, "username": "lisi", "nickname": "李四", "avatarUrl": "...", "role": 3 }
    ],
    "createdTime": "2026-05-26T20:00:00"
  }
}
```

### 5.4 修改群信息

```
PUT /api/groups/{groupId}

Request:
{
  "name": "新群名",              // 可选
  "description": "群简介",       // 可选
  "notice": "群公告内容",         // 可选
  "allowInvite": true            // 可选
}

权限: 群主或管理员
```

### 5.5 邀请成员

```
POST /api/groups/{groupId}/members

Request:
{
  "userIds": [10005, 10006]
}

校验:
  - 群存在且未解散
  - 邀请人必须是成员（且满足 allow_invite 配置）
  - 被邀请人不能已经在群里
  - 不超 max_members

逻辑:
  - INSERT INTO group_member（批量），role=3
  - UPDATE group_chat SET member_count = member_count + N
```

### 5.6 移除成员

```
DELETE /api/groups/{groupId}/members/{userId}

权限: 群主或管理员，或自己退出
逻辑:
  - 不能移除群主
  - DELETE FROM group_member
  - UPDATE group_chat SET member_count = member_count - 1
```

### 5.7 退出群

```
POST /api/groups/{groupId}/leave

逻辑:
  - 群主不能退出（需先转让或解散）
  - DELETE FROM group_member WHERE ...
  - UPDATE group_chat SET member_count = member_count - 1
```

### 5.8 解散群

```
POST /api/groups/{groupId}/dissolve

权限: 仅群主
逻辑:
  - UPDATE group_chat SET status = 1
  - 群成员记录保留（用于历史消息查看），不做物理删除
```

### 5.9 设置管理员

```
PUT /api/groups/{groupId}/members/{userId}/role

Request:
{ "role": 2 }    // 2=管理员, 3=普通成员

权限: 仅群主
```

---

## 6. 前端交互流程

### 6.1 创建聊天入口

通讯录 250px 左侧列表顶部：

```
┌─────────────────────────┐
│  [🔍 搜索好友...]  [+]  │  ← 点击 [+] 触发创建流程
└─────────────────────────┘
```

### 6.2 点击 `[+]` 后的流程

```
┌──────────────────────────────────────────┐
│  选择联系人               [创建] [取消]    │
├──────────────────────────────────────────┤
│  🔍 搜索好友...                           │
├──────────────────────────────────────────┤
│  ☑ [头像] 张三                            │
│  ☑ [头像] 李四                            │
│  ☐ [头像] 王五                            │
│  ☐ [头像] 赵六                            │
│  ...                                     │
└──────────────────────────────────────────┘
```

- 展示好友列表，每项左侧 checkbox
- 支持搜索过滤
- 顶部显示已选中人数和 [创建] 按钮

### 6.3 分支：1 人 vs 多人

**选 1 人 → 私聊：**
```
1. 调用后端检查/创建私聊会话
   POST /api/conversations/private
   { "peerId": 10002 }

2. 后端返回 conversationId = "p_10001_10002"

3. 前端跳转 /chat/p_10001_10002
```

**选多人 → 创建群聊：**
```
点击 [创建] → 弹出群设置对话框：

┌──────────────────────────┐
│  创建群聊                 │
├──────────────────────────┤
│  群头像：[上传]          │
│                          │
│  群名称：[___________]   │  ← 必填
│                          │
│  已选成员：               │
│  [头像] 张三 [头像] 李四  │
│                          │
│         [取消] [确认创建] │
└──────────────────────────┘
```

确定后调 `POST /api/groups`，拿到 `conversationId` → 跳转 `/chat/g_10000`。

---

## 7. 通讯录左侧群列表

"我的好友"下方已有的"我的群聊"区域：

```
┌─────────────────────────┐
│  ── 我的好友 ──         │
│  [好友1] [好友2] ...    │
│                          │
│  ── 我的群聊 ──         │
│  [群头像] 开发小组 (5)   │  ← 群名 + 成员数
│  [群头像] 周末篮球 (12)  │
└─────────────────────────┘
```

---

## 8. 群详情右面板

点击群 → 右栏展示（参考前端 reference）：

```
┌──────────────────────────────────┐
│  ┌────────────────────────────┐  │
│  │ [群头像]  开发小组           │  │
│  │           群主：张三         │  │
│  │           成员数：5          │  │
│  └────────────────────────────┘  │
│ ──────────────────────────────── │
│  群简介    开发相关技术讨论       │
│  群公告    @所有人 周五发版本    │
│  邀请权限   所有人 / 仅管理员    │
│ ──────────────────────────────── │
│  群成员 (5)                      │
│  [头像] 张三  群主               │
│  [头像] 李四  管理员             │
│  [头像] 王五                     │
│  [头像] 赵六                     │
│  [头像] 钱七                     │
│ ──────────────────────────────── │
│  [发消息]  [邀请成员]  [退出群]   │
└──────────────────────────────────┘
```

---

## 9. 实现顺序

1. schema.sql 新增 group_chat + group_member + conversation 表
2. id_segment 初始化 group 号段
3. Entity: GroupChat, GroupMember, Conversation
4. DTO: CreateGroupRequest, GroupVO, GroupDetailVO, GroupMemberVO
5. Mapper: GroupChatMapper, GroupMemberMapper, ConversationMapper + XML
6. GroupService 业务逻辑
7. GroupController
8. ConversationController（创建私聊会话、获取会话列表）
9. 更新 UserMapper: 搜索好友时支持多选
10. 更新 IdGenerator: 支持 group 号段
11. 前端: 联系人选择器 + 创建群聊对话框 + 群详情面板
12. 单元测试
