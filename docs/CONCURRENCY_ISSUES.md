# HappyIM 并发问题审计报告

## 高危 (HIGH)

### 1. WebSocket 并发 sendMessage() 导致帧损坏

**位置**: [ChatWebSocketHandler.java:118](../backend/happyim-ws/src/main/java/com/happyim/ws/handler/ChatWebSocketHandler.java#L118)

**问题**: RabbitMQ 消费者配置了 `concurrentConsumers=2, maxConcurrentConsumers=5`，最多 5 个线程可同时对同一用户的 session 调用 `sendMessage()`。Spring 的 `WebSocketSession` 底层实现不是线程安全的，并发写会产生交错的/损坏的 WebSocket 帧。

**场景**:
```
线程A: session.sendMessage(msg1) ──bytes── "abc"──
线程B: session.sendMessage(msg2) ──bytes── "xyz"──
→ 客户端收到 "axbycz"（交错损坏）
```

**修复方案**: 在 `pushMessage`/`pushNotification`/`pushEvent` 中对同一用户的 session 加锁，或使用 `synchronized(session)` 块，或改为单线程消费者。

---

### 2. WebSocket session.getAttributes() HashMap 并发读写

**位置**: [ChatWebSocketHandler.java:104](../backend/happyim-ws/src/main/java/com/happyim/ws/handler/ChatWebSocketHandler.java#L104)

**问题**: `session.getAttributes()` 底层是普通 `HashMap`。WebSocket 线程在 `handleTextMessage` 中写入（用户切换会话），RabbitMQ 消费者线程在 `getCurrentConversation` 中读取。HashMap 并发读写可导致死循环（CPU 100%）或 NPE。

**场景**:
```
WebSocket线程: attributes.put("currentConversation", "newId")  ← 写
消费者线程:   attributes.get("currentConversation")             ← 读
→ HashMap rehash 时读取 → 死循环 / NPE
```

**修复方案**: 将 `currentConversation` 从 `session.attributes` 移到独立的 `ConcurrentHashMap<Long, String>` 中。

---

### 3. 朋友圈/广场点赞 $pull + $push 非原子操作

**位置**: [MomentService.java:108](../backend/happyim-api/src/main/java/com/happyim/api/service/MomentService.java#L108) / SquareService 同位置

**问题**: `unlike` 和 `like` 都是先 `$pull` 再 `$push`，两步不原子。并发 like 同一贴时：

```
线程A: $pull (无已有like)
线程B: $pull (无已有like)
线程A: $push (加入 like-A)
线程B: $push (加入 like-B)
→ likes 数组有两个相同 userId 的条目
```

**修复方案**: 用 `$addToSet` 替代 `$pull + $push`，单次原子操作。

---

### 4. 朋友圈/广场 deleteComment() 读-改-写竞态

**位置**: [MomentService.java:160](../backend/happyim-api/src/main/java/com/happyim/api/service/MomentService.java#L160) / SquareService 同位置

**问题**: `findById` 读取整个文档 → 修改 Java List → `set("comments", list)` 写回。并发删除时会互相覆盖：

```
线程A: 读取 comments=[C0,C1,C2], 删除索引1 → [C0,C2]
线程B: 读取 comments=[C0,C1,C2], 删除索引2 → [C0,C1]
线程A: 写回 [C0,C2]
线程B: 写回 [C0,C1]  ← C2 被复活
→ 线程A 删的评论 C1 消失了，C2 又回来了
```

**修复方案**: 用 MongoDB `$pull` 按条件直接删除，避免读-改-写：`new Update().pull("comments", Query.query(Criteria.where("userId").is(userId)))`。

---

## 中危 (MEDIUM)

### 5. 群组 addMembers 重复插入同一成员

**位置**: [GroupService.java:170](../backend/happyim-api/src/main/java/com/happyim/api/service/GroupService.java#L170)

**问题**: `addMembers` 先查 `findByGroupAndUser` 再 `insert`，无 UNIQUE 约束。并发添加同一用户时，两次查询都返回 null，两次 insert 都成功。

**修复方案**: `group_member` 表加 `UNIQUE KEY (group_id, user_id)`，insert 改为 `INSERT IGNORE`。

---

### 6. 注册并发重名抛原始异常

**位置**: [AuthService.java:69](../backend/happyim-api/src/main/java/com/happyim/api/service/AuthService.java#L69)

**问题**: 并发注册同名用户 → 都通过 `findByUsername` 检查 → 第二个 `INSERT` 触发数据库 UNIQUE 约束 → 抛出 `DuplicateKeyException`，用户看到 500 错误而非"用户名已存在"。

**修复方案**: 捕获 `DataIntegrityViolationException`，转为 `BizException(ErrorCode.USERNAME_ALREADY_EXISTS)`。

---

### 7. 好友申请重复发送

**位置**: [FriendService.java:88](../backend/happyim-api/src/main/java/com/happyim/api/service/FriendService.java#L88)

**问题**: 并发 `sendRequest(A→B)`，都查到无 pending，都 insert 成功。`friend_request` 表无 `(from_user_id, to_user_id, status=0)` 唯一约束。

**修复方案**: 加唯一约束或使用 `INSERT IGNORE`。

---

### 8. 成员加入已解散的群

**位置**: [GroupService.java:172 vs 254](../backend/happyim-api/src/main/java/com/happyim/api/service/GroupService.java#L172)

**问题**: `addMembers` 读取群状态 `status=0` → 另一个线程执行 `dissolveGroup` 设 `status=1` → `addMembers` 继续插入成员到已解散的群。

**修复方案**: 在 `addMembers` 事务中 `SELECT ... FOR UPDATE` 锁住群行，或插入前再次检查状态。

---

### 9. WebSocket pushMessage 异常传播

**位置**: [ChatWebSocketHandler.java:118](../backend/happyim-ws/src/main/java/com/happyim/ws/handler/ChatWebSocketHandler.java#L118)

**问题**: `isOpen()` 检查后、`sendMessage()` 前，连接断开。异常未被捕获，传播到 RabbitMQ 消费者，触发消息重投循环。

**修复方案**: 在 push 方法内 catch 异常，记录 warn 日志，不向外抛。

---

### 10. sendMessage 无事务保护

**位置**: [MessageService.java:77](../backend/happyim-api/src/main/java/com/happyim/api/service/MessageService.java#L77)

**问题**: `sendMessage` 无 `@Transactional`。MongoDB 写入成功后 Redis/MQ 写入失败 → 消息文档存在但无 feed 条目引用，消息不可见。

**修复方案**: 加 `@Transactional` 或调整写入顺序（先写消息到 MongoDB → 确认成功 → 再写 feed）。

---

## 低危 (LOW)

| # | 位置 | 问题 |
|---|------|------|
| 11 | IdGenerator | `replenishSegment` 中冗余的读写判断，非 bug |
| 12 | MessageIdGenerator | Redis INCR 超过 4096/ms 同会话时理论碰撞，实际不可能 |
| 13 | MessageService | `redisTemplate.keys()` 阻塞 Redis 单线程，性能隐患 |

---

## 已有正确实现的并发保护

| 位置 | 机制 | 评价 |
|------|------|------|
| `IdGenerator.replenishSegment()` | `synchronized` + Redis SETNX 分布式锁 | 正确 |
| `SensitiveWordFilter.reload()` | `synchronized` + `volatile` | 正确 |
| `IdGenerator.nextId()` | Redis INCR（原子） | 正确 |
| `MessageIdGenerator.incrementSpin()` | Redis INCR（原子） | 正确 |
| `GroupChatMapper.updateMemberCount()` | SQL `SET member_count = member_count + N`（原子） | 正确 |
| `FriendMapper.insert()` | `INSERT IGNORE` | 正确 |
| `ConversationMapper.insert()` | `INSERT IGNORE` | 正确 |
| `MessageService.incrementUnread()` | Redis HINCRBY（原子） | 正确 |
| `ChatWebSocketHandler.sessions` | `ConcurrentHashMap` | 正确 |
