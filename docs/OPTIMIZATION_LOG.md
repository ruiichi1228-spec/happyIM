# 优化日志

## 1. 并发安全修复 ✅

### 1.1 WebSocket 并发 sendMessage（高危 #1）

**问题**: RabbitMQ 最多 5 个消费者线程同时对同一 session 调用 `sendMessage()`，Spring WebSocket 底层非线程安全，并发写导致 WebSocket 帧损坏。

**修复**: `ChatWebSocketHandler.java`
- 新增 `sendMessageSafe()` 方法，内部对 session 加 `synchronized` 锁
- `pushMessage` / `pushNotification` / `pushEvent` 全部改为调用 `sendMessageSafe`
- 推送方法不再抛异常，异常内部 catch log

### 1.2 WebSocket session.attributes 并发读写（高危 #2）

**问题**: `session.getAttributes()` 返回普通 `HashMap`。WebSocket 线程 `handleTextMessage` 写 `currentConversation`，RabbitMQ 消费者线程 `getCurrentConversation` 读 —— HashMap 并发读写导致 CPU 死循环或 NPE。

**修复**: `ChatWebSocketHandler.java`
- 新增 `ConcurrentHashMap<Long, String> currentConversations`
- `handleTextMessage` 的 enter/leave 操作改为读写这个 Map
- `getCurrentConversation` 改为查这个 Map
- `afterConnectionClosed` 里清理 `currentConversations.remove(userId)`

### 1.3 朋友圈/广场点赞非原子操作（高危 #3）

**问题**: `$pull` + `$push` 两步非原子，并发 like 同一帖导致重复条目：线程 A pull(空) → 线程 B pull(空) → A push → B push = 两条同样的 like。

**修复**: `MomentService.java` + `SquareService.java`
- `$pull` + `$push` 替换为 `$addToSet`（MongoDB 原子操作）
- `$addToSet` 语义：元素不存在则添加，已存在则跳过

### 1.4 朋友圈/广场评论删除竞态（高危 #4）

**问题**: `findById` 整个文档 → `list.remove(index)` → `set("comments", list)` 写回。并发删除时：线程 A 删索引 1 → 线程 B 删索引 2（基于旧 snapshot）→ A 写回 → B 写回（覆盖 A 的修改）。

**修复**: `MomentService.java` + `SquareService.java`
- 删除整个读-改-写逻辑
- 改用 `$pull` 按 `userId` 匹配直接删除（MongoDB 原子操作）

---

## 2. MQ 序列化统一 ✅

**问题**: user-service / chat-service 用 Java 序列化发消息，chat-ws / content-service 期望 JSON，反序列化报 `Cannot convert from [B] to [java.util.Map]`。

**修复**:
- `chat-service/RabbitMQConfig.java` — 添加 `Jackson2JsonMessageConverter` bean
- `content-service/RabbitMQConfig.java` — 添加 `Jackson2JsonMessageConverter` bean
- `user-service/RabbitMQConfig.java` — 新建配置类，添加 `Jackson2JsonMessageConverter` bean

---

## 3. MQ 路由键统一 ✅

**问题**: chat-service 发到 `chat.message`，chat-ws 只监听 `ws.ws-1`，消息送不到。

**修复**: 统一 exchange 为 `happyim.exchange`，chat-ws 监听队列绑定 4 个路由键：`chat.message`、`notify.moment`、`notify.square`、`notify.friend`。

---

## 4. Gateway 联调修复 ✅

- 添加 `spring-cloud-starter-loadbalancer` 依赖（修复 503）
- 添加 `spring-cloud-starter-alibaba-nacos-config` 依赖（修复配置拉取）
- JWT 密钥统一到 Nacos `happyim-common.yaml`
- 所有服务 `spring.cloud.nacos.discovery.ip=127.0.0.1`（修复 APIPA 注册）
- Gateway `server.address=0.0.0.0`（修复 IPv6 绑定）
- `/ws` 路由转发到 chat-ws

---

## 5. 清理旧模块 ✅

**删除**: `happyim-api` + `happyim-ws`（功能已被微服务替代）
**保留**: `happyim-common`（Mapper / Entity / JWT 共享类库，后续逐步拆分）

---

## 6. 即时消息推送修复 ✅

**问题**: 对方收不到消息、朋友圈通知无法推送。

**修复**: chat-ws MQ exchange 从 `chat.exchange` 改为 `happyim.exchange`，routing-key 从 `ws.ws-1` 改为 `chat.message`。

---

## 7. 解散群组消息拦截 ✅

**问题**: 群已解散但仍能发消息。

**修复**: `MessageService.sendMessage()` 增加群 status 检查，`status == 1` 时返回错误。

---

## 8. Redis / Service-to-Service 调用修复 ✅

**问题**: content-service 连不上 Redis（排行榜报错），调用 `/api/friends` 返回 500。

**修复**:
- `content-service/application.yml` 添加 Redis 配置
- `user-service/FriendController` 添加 `GET /api/users/{userId}/friends` 内部端点
- `MomentService` 将 `friendId` 改为 `userId`（匹配 FriendVO 字段名）
