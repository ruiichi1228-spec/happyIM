# HappyIM 优势与不足分析

## 一、架构优势

### 1. 模块化清晰，共享层设计合理

`happyim-common` 抽取了实体、DTO、Mapper、工具类、安全模块，api 和 ws 两个服务零冗余代码。新增一个服务只需依赖 common，不会出现"每个服务自己写一套 User 实体"的情况。

### 2. 混合数据库，各取所长

| 数据类型 | 存储 | 为什么 |
|---------|------|--------|
| 用户/好友/群组 | MySQL | 强一致性、JOIN 查询、事务 |
| 消息/朋友圈/广场 | MongoDB | 海量写入、schema 灵活、嵌入式文档 |
| Session/缓存/在线 | Redis | 低延迟、原子操作、TTL |
| 文件 | MinIO | 对象存储，S3 兼容，水平扩展 |

不是一刀切的"全用 MySQL"或"全上 MongoDB"，而是按数据特征选型。

### 3. WebSocket 解耦

WebSocket 服务独立进程 (Port 8081)，通过 RabbitMQ 接收消息推送：

- API 服务不用维护长连接，专注业务逻辑
- WS 服务可以独立扩缩容（虽然当前是单实例）
- MQ 提供了消息缓冲，削峰填谷

### 4. 通知由轮询改为实时推送

好友申请、朋友圈点赞评论、广场通知全部走 MQ → WS 推送，前端不再用 `setInterval` 轮询。事件驱动，零延迟零浪费。

### 5. 前端缓存层减少请求

`userCache.js` 模块级单例，消息只传 `fromUserId`，前端首次遇到时批量请求用户信息，后续直接内存命中。好友列表、群成员自动灌入。

### 6. 消息 ID 自编码，全局有序

80-bit 自定义 ID (42bit 时间戳 + 12bit spin + 4bit type + 22bit CRC)，字符串字典序 = 时间序，前端直接 `localeCompare` 排序，不需要额外的时间戳字段。

### 7. 暗色模式系统化

五级色板 (`--bg-nav` → `--bg-sidebar` → `--bg-primary` → `--bg-card` → `--bg-input`) 建立视觉层次，不是"一片黑"。所有 Element Plus 组件、scoped 样式统一覆盖。

### 8. Docker Compose 一键部署

MySQL、MongoDB、Redis、RabbitMQ、MinIO 全部容器化，一个 `docker compose up -d` 启动所有基础设施。

---

## 二、架构不足与改进方向

### 1. WebSocket 单实例瓶颈

**问题**：`ConcurrentHashMap<userId, WebSocketSession>` 是进程内内存，无法跨实例共享。当前只能跑一个 WS 实例。

**改法**：引入 Redis Pub/Sub 广播，或采用"用户路由到固定实例"方案：
- Redis `router:user:{id}` 存 WS 实例 ID
- API 查 Redis → 投递到对应实例的 MQ Queue
- 新增实例自动注册新的 Queue Binding

### 2. 消息 Feed 写入冗余

**问题**：群聊 N 个成员，每条消息写 N 条 Feed 到 MongoDB。100 人群聊一条消息 = 100 次 MongoDB insert。Feed 膨胀速度快。

**改法**：读时扩散 (Inbox) 模式
- 取消 Feed 写入，消息只存 `messages` 集合
- 读消息历史时：私聊查双方消息，群聊查群消息，按时间排序
- 优点：写放大消失；缺点：读时查询稍复杂

### 3. session hash 字段太多

**问题**：`chat:session:{uid}:{convId}` 存了 10+ 字段 (type, peer_id, peer_name, peer_avatar, last_msg_content...)，内存占用高。10 万用户 × 平均 50 个会话 = 500 万个 Hash Key。

**改法**：分离热数据和冷数据
- 热数据 (unread_count, last_msg_content, last_msg_time, read_cursor) 存 Redis
- 冷数据 (peer_name, peer_avatar) 按需从 MySQL/MongoDB 查询
- 或使用 RedisJSON 压缩存储

### 4. 群成员列表全量加载

**问题**：进入群聊时 `GET /groups/{id}/members` 返回全部成员，500 人大群浪费带宽。

**改法**：分页加载 + 虚拟滚动
- 成员列表分页 (50 人/页)
- 前端虚拟滚动只渲染可见区域
- 搜索走后端 `LIKE` 查询

### 5. 离线消息推送缺失

**问题**：用户离线时，MQ Consumer 检测不到在线直接跳过，没有任何离线补偿机制。

**改法**：APNs / FCM 推送
- 离线消息写入 Redis List 或 MongoDB 待推送队列
- 用户上线时拉取未推送的消息
- 或接入 Firebase Cloud Messaging / Apple Push Notification

### 6. 缺少消息已读回执

**问题**：只有"会话级"已读 (`unread_count = 0`)，没有"消息级"已读 (单条消息的送达/已读状态)。群聊中无法知道谁读了谁没读。

**改法**：
- 单聊：每个消息的 `readBy` 字段记录已读用户的 `messageId` 游标
- 群聊：这个功能代价高，一般不做，可以用"未读数"代替

### 7. 朋友圈时间线全量扫描

**问题**：`getTimeline()` 每次从 MongoDB 查询 `userId in (friends + self)`，好友多时扫描量大。

**改法**：Feed 扇出模式
- 发布时主动写入所有好友的时间线 Feed
- 读时间线只需查自己的 Feed，O(1) 复杂度
- 代价：写放大 (发一条 = 写 N 条)

### 8. 缺失 API 文档和单元测试

**问题**：没有 Swagger/OpenAPI 文档，没有自动化测试，新接手的人难以理解接口。

**改法**：引入 springdoc-openapi + JUnit 5 + MockMvc

### 9. 敏感词过滤过于简单

**问题**：AC 自动机仅做字符级匹配，无法处理拼音、谐音、拆分字等变体。

**改法**：接入第三方内容安全服务 (如阿里云内容安全 API)

---

## 三、性能预估

| 指标 | 预估 | 瓶颈 |
|------|------|------|
| 单 API 实例 QPS | ~2000 | MySQL 连接池 |
| 单 WS 实例连接数 | ~5000 | JVM 内存 (HashMap) |
| MongoDB 写入 | 不限 (水平扩展) | 分片键设计 |
| Feed 查询 | ~50ms | {userId, conversationId, messageId} 复合索引 |
| 未读计数 | ~1ms | Redis Hash 直接读取 |

单体架构下，日活 1 万以内完全够用。10 万 DAU 以上建议微服务拆分。
