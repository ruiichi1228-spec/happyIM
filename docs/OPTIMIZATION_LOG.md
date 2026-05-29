# 优化日志

## 1. 并发安全修复

### 1.1 WebSocket 并发 sendMessage（高危 #1）

**问题**: RabbitMQ 最多 5 个消费者线程可同时对同一 session 调用 `sendMessage()`，WebSocket 底层不是线程安全的。

**修复**: 推送时对 session 加 `synchronized` 锁，确保同一用户的推送串行化。

### 1.2 WebSocket session.attributes 并发读写（高危 #2）

**问题**: `session.getAttributes()` 返回普通 HashMap，WebSocket 线程写、消费者线程读，并发导致死循环/NPE。

**修复**: 当前会话 ID 从 `session.attributes` 移到独立 `ConcurrentHashMap<Long, String>`。

### 1.3 朋友圈/广场点赞非原子操作（高危 #3）

**问题**: `$pull` + `$push` 两步非原子，并发 like 同一帖导致重复条目。

**修复**: 改用 `$addToSet` 单次原子操作。

### 1.4 朋友圈/广场评论删除竞态（高危 #4）

**问题**: `findById` → 修改 List → `set` 写回，并发删除时互相覆盖。

**修复**: 改用 `$pull` 直接删除，避免读-改-写。

---

## 2. RestTemplate → Feign

**问题**: content-service 和 chat-ws 手写 RestTemplate URL 调用 user-service，URL 硬编码、类型不安全。

**修复**:
- content-service `UserServiceClient` → 注入 `UserFeignClient`
- chat-ws `MessageConsumer.getSenderName()` → 注入 `UserFeignClient`
- 删掉 RestTemplate 配置类

---

## 3. 清理旧模块

**问题**: `happyim-api` 和 `happyim-ws` 已不维护，每次构建拖慢速度。

**修复**: 从根 pom.xml 移除两个模块。

---

## 4. MyBatis-Plus 替换手写 XML

**问题**: XML Mapper 维护成本高。

**修复**: Entity 加注解，Mapper 继承 `BaseMapper<T>`，分页用 `LambdaQueryWrapper + Page<T>`。

---

## 5. Guava 本地缓存

**问题**: user-service 每次查用户信息都走 MySQL。

**修复**: 加 `LoadingCache<Long, User>`，5 分钟过期，最大 1000 条。
