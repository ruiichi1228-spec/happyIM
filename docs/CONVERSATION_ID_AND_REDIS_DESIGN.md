# HappyIM 用户ID / 群ID / 会话ID / Redis摘要 设计

## 用户 ID 与群 ID 设计

### 格式

```
用户 ID:  纯数字，从 10000 开始递增，如 10000, 10001, 10002 ...
群 ID:    纯数字，从 10000 开始递增，如 10000, 10001, 10002 ...
机器人ID: 字母标识，如 weather_bot, translate_bot
```

### 生成机制：MySQL 号段 + Redis 预分配

数据库是真实数据源，Redis 是预分配缓冲区。

#### MySQL 表

```sql
CREATE TABLE `id_segment` (
    `biz_tag`   VARCHAR(32) PRIMARY KEY COMMENT '业务标识',
    `max_id`    BIGINT NOT NULL              COMMENT '当前已分配的最大ID',
    `step`      INT NOT NULL DEFAULT 1000    COMMENT '每次预分配的号段长度',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='ID号段表';

-- 初始化
INSERT INTO id_segment VALUES ('user',  10000, 1000, NOW());
INSERT INTO id_segment VALUES ('group', 10000, 1000, NOW());
```

#### Redis 预分配

```
Key: id:segment:user     → String，存储当前号段的剩余区间（如 "11001-12000"）
Key: id:user:next        → String (INCR)，从号段起点开始自增分配
```

#### 分配流程

```
后端实例请求生成用户 ID：

正常路径（Redis）:
  Redis INCR id:user → 返回 10001
  ↓
  当前号段是 [10001, 11000]，INCR 到 11001 时号段耗尽
  ↓
  Redis Lua 脚本检查余量 → 不足

补充路径（MySQL → Redis）:
  UPDATE id_segment SET max_id = max_id + step WHERE biz_tag = 'user'
  → 拿到新号段 [11001, 12000]
  → SET id:segment:user "11001-12000"
  → Redis INCR 从 11001 继续

MySQL 挂了的兜底:
  所有实例都处于号段耗尽状态 → 暂时无法生成 ID → 返回错误
  此时需要 MySQL 恢复
```

#### 关键点

| 问题 | 说明 |
|------|------|
| 中间断层？ | 有。号段 [11001-12000] 被实例拿走但只用了前 10 个就宕机，余下 990 个浪费。bigint 够用 922 京，不碍事 |
| 号码回收？ | 不回收。QQ 也不回收 |
| 分布式安全？ | 安全。每个号段只给一个实例，各实例独立消费，不可能碰撞 |
| 性能？ | 正常走 Redis INCR，毫秒级。号段耗尽时多一次 MySQL UPDATE，分摊到 1000 次请求发生一次 |

---

## 会话 ID 设计

### 格式

```
{type}_{identifier}

类型前缀:
  p_  私聊 (personal)
  g_  群聊 (group)
  b_  机器人 (bot)
  s_  系统通知 (system)
```

### 规则

| 类型 | 格式 | 示例 | 说明 |
|------|------|------|------|
| 私聊 | `p_{id1}_{id2}` | `p_10000_10001` | 两个用户 ID 按字典序排序后拼接，确保对称性 |
| 群聊 | `g_{groupId}` | `g_10000` | 直接用群 ID |
| 机器人 | `b_{userId}_{botId}` | `b_10000_weather_bot` | 用户和机器人一对一 |
| 通知 | `s_{userId}` | `s_10000` | 每个用户一个系统通知会话 |

### 设计要点

- **私聊对称性**：两个用户按字典序排序后拼接，10000 发给 10001 和 10001 发给 10000 得到同一个 `p_10000_10001`，无需查表即可确定
- **前缀区分**：一眼可辨类型，前端和后端都能从前缀做差异化逻辑
- **可解析性**：不查数据库就能推断会话类型和参与者
- **索引友好**：前缀固定，MongoDB B+树索引性能好
- **扩展性**：未来加新类型（如频道、直播）直接加前缀

### 机器人 vs 普通私聊

机器人本质上也是私聊，但用 `b_` 前缀单独区分，原因：
- 机器人可能在好友列表中不可见或带特殊标识
- 机器人消息可能需调用外部 API
- 聊天窗口可能需要显示功能菜单（天气、翻译等）

---

## Redis 会话摘要设计

### 数据结构（3 块）

```
1. ZSET — 会话时间线
   Key:  chat:sessions:{userId}
   Score: last_message_time
   Member: conversation_id

2. ZSET — 置顶会话
   Key:  chat:sessions:pinned:{userId}
   Score: pinned_at (置顶时间戳)
   Member: conversation_id

3. Hash — 单个会话摘要详情
   Key:  chat:session:{userId}:{conversationId}
```

### Hash 字段

| Field | 类型 | 示例 | 说明 |
|-------|------|------|------|
| `type` | int | `0` | 0-私聊 1-群聊 2-机器人 3-通知 |
| `peer_id` | string | `10001` | 对方 ID（私聊/机器人时使用） |
| `peer_name` | string | `张三` | 对方昵称或群名（冗余，避免列表渲染查表） |
| `peer_avatar` | string | `http://...` | 对方头像或群头像 URL |
| `last_msg_id` | string | `BD8U-xxx` | 最后一条消息 ID |
| `last_msg_content` | string | `你好，明天见...` | 最后消息预览（截断 50 字） |
| `last_msg_type` | string | `text` | text / image / file / recall |
| `last_msg_time` | int64 | `1716123456789` | 最后消息时间戳 |
| `last_sender_id` | string | `10001` | 最后一条消息的发送者 |
| `unread_count` | int | `3` | 未读计数 |
| `read_cursor` | string | `BD8U-xxx` | 我已读到的最后一条消息 ID |
| `is_muted` | int | `0` | 0-正常 1-免打扰 |
| `updated_at` | int64 | `1716123456789` | 会话最后活跃时间 |

### TTL

- ZSET 和 Hash: **3 个月**（活跃会话保留，定期清理过期 key）

---

## 操作流程

### 获取会话列表

```
1. ZRANGE  chat:sessions:pinned:{userId} 0 -1         → 置顶会话列表
2. ZREVRANGE chat:sessions:{userId} 0 N  WITHSCORES   → 最近会话（按时间倒序）
3. 对每个 conversation_id:
     HGETALL chat:session:{userId}:{conversationId}    → 摘要详情
4. 合并返回给前端
```

### 发送消息时更新

用户 A 给用户 B 发消息，用 Pipeline 原子操作：

```
# === 更新 A 自己的摘要（发送者） ===
HMSET chat:session:10000:p_10000_10001
  last_msg_id      = "BD8U-xxx"
  last_msg_content = "你好"
  last_msg_type    = "text"
  last_msg_time    = "1716123456789"
  last_sender_id   = "10000"
  unread_count     = "0"              ← 发送者无未读
  read_cursor      = "BD8U-xxx"       ← 发送者自动标记已读

ZADD chat:sessions:10000 1716123456789 p_10000_10001

# === 更新 B 的摘要（接收者） ===
HMSET chat:session:10001:p_10000_10001
  last_msg_id      = "BD8U-xxx"
  last_msg_content = "你好"
  last_msg_type    = "text"
  last_msg_time    = "1716123456789"
  last_sender_id   = "10000"

HINCRBY chat:session:10001:p_10000_10001 unread_count 1   ← 原子递增未读

ZADD chat:sessions:10001 1716123456789 p_10000_10001
```

### 用户已读消息时

```
HSET chat:session:10001:p_10000_10001
  unread_count = "0"
  read_cursor  = "BD8U-xxx"   ← 当前读到的最新消息 ID
```

### 置顶 / 取消置顶

```
# 置顶
ZADD chat:sessions:pinned:10000 1716123456789 p_10000_10001

# 取消置顶
ZREM chat:sessions:pinned:10000 p_10000_10001
```

### 免打扰

```
HSET chat:session:10000:p_10000_10001 is_muted 1
```

免打扰会话仍然正常更新 unread_count（后端记录），但前端不显示红点数字。

---

## 设计要点

- **peer_name / peer_avatar 冗余**：会话列表不查数据库直接渲染。对方改名时批量更新相关 Hash 即可（量不大）。
- **ZSET 双表**：置顶和普通分开，查询简单，置顶取消后回退到普通 ZSET。
- **Pipeline 原子操作**：发消息时对两个用户的摘要更新放在一个 Pipeline 中，保证一致性。
- **未读计数用 HINCRBY**：原子递增，避免并发覆盖问题。
