# HappyIM 微服务拆分方案

## Context

当前是 3 个 Maven 模块的单体应用（common / api / ws）。拆分目标：展示微服务架构能力，同时保持可维护性。3 个业务服务 + Gateway + Nginx + WS 独立部署 = 6 个运行时组件。

## 目标架构

```
                              ┌─────────────────┐
                              │   Nginx     │  SSL · 前端 · IP黑名单 · 限流
                              │   port 80/443   │  Lua 脚本边缘处理
                              └────────┬────────┘
                        /api/*         │         /*
                         │             │          │
                  ┌──────▼──────┐      │   ┌──────▼──────┐
                  │   Gateway   │      │   │   前端 dist  │
                  │  port 8080  │      │   │  (Nginx)    │
                  └──────┬──────┘      │   └─────────────┘
         ┌───────────────┼─────────────┼───────────────┐
         │               │             │               │
   ┌─────▼─────┐  ┌──────▼──────┐ ┌───▼────────┐      │
   │   user    │  │    chat     │ │  content   │      │
   │ -service  │  │  -service   │ │  -service  │      │
   │  :8101    │  │   :8102     │ │   :8103    │      │
   └─────┬─────┘  └──────┬──────┘ └─────┬───────┘      │
         │               │              │              │
   ┌─────▼─────┐  ┌──────▼──────┐ ┌─────▼───────┐ ┌────▼──────┐
   │  MySQL    │  │ MongoDB     │ │  MongoDB    │ │  MinIO    │
   │ (user+    │  │ (messages,  │ │ (moments,   │ │           │
   │  group)   │  │  feed)      │ │  square,    │ │           │
   │           │  │  Redis      │ │  file_feed) │ │           │
   │           │  │  +MySQL     │ │             │ │           │
   └───────────┘  └─────────────┘ └─────────────┘ └───────────┘

   ┌──────────────┐      ┌──────────────┐
   │  chat-ws     │      │    Nacos     │  服务注册/发现 + 配置中心
   │   :8081      │      │   :8848      │
   └──────┬───────┘      └──────────────┘
          │
   ┌──────▼───────┐
   │   RabbitMQ   │  异步消息
   └──────────────┘
```

## 服务划分

| 服务 | 端口 | 职责 | 自有数据源 |
|------|------|------|-----------|
| **openresty** | 80/443 | SSL、前端 dist、边缘限流、IP 黑名单（Lua）、WS 代理 | - |
| **gateway** | 8080 | 动态路由、JWT 验签、Sentinel 限流熔断 | - |
| **user-service** | 8101 | 注册/登录/Token/个人资料/好友/群组/管理员 | MySQL + MyBatis-Plus + Guava Cache |
| **chat-service** | 8102 | 消息收发/会话/已读/撤回/敏感词过滤 | MySQL + MyBatis-Plus; MongoDB; Redis; RabbitMQ |
| **content-service** | 8103 | 朋友圈/广场/文件上传下载 | MongoDB; MinIO |
| **chat-ws** | 8081 | WebSocket 长连接、实时消息推送 | Redis: online状态, 路由表 |

### 为什么这样拆？

- **user-service** — 所有关系型数据归它。user/friend/group 本来就是紧密耦合的（拉群、加好友都涉及用户表），拆开反而增加跨服务调用
- **chat-service** — IM 的核心。消息量最大，需要独立扩容。WebSocket 逻辑独立部署为 chat-ws（长连接进程需要独立管理）
- **content-service** — 社交内容 + 文件，都是 MongoDB + MinIO 为主，对 MySQL 零依赖，天然独立

## 基础设施

| 组件 | 用途 | 状态 |
|------|------|------|
| **Nginx + Lua** | SSL 终结、前端静态资源、边缘限流、IP 黑名单、WebSocket 代理 | 新增（替代普通 Nginx）|
| **Nacos** | 服务注册发现 + 配置中心 | 新增 |
| **Spring Cloud Gateway** | 动态路由、JWT 验签 | 新增 |
| **Sentinel** | 网关层 + 服务层限流熔断 | 新增 |
| **OpenFeign** | 服务间同步调用 | 新增 |
| **RabbitMQ** | 异步消息（chat 推送、通知事件）| 保留 |
| **MyBatis-Plus** | 分页、Lambda 查询、代码生成，替代手写 XML | 新增 |
| **Guava Cache** | 用户昵称/头像本地缓存，减少 Redis 调用 | 新增 |
| **Prometheus + Grafana** | 各服务 QPS、延迟、JVM、MQ 积压监控 | 新增 |
| **JMeter** | 拆完后压测，出性能报告 | 新增 |

## Gateway 路由规则

```
/api/auth/**          → user-service
/api/users/**         → user-service
/api/friends/**       → user-service
/api/groups/**        → user-service
/api/admin/**         → user-service
/api/conversations/** → chat-service
/api/moments/**       → content-service
/api/square/**        → content-service
/api/files/**         → content-service
/api/file-feed/**     → content-service
/ws                   → chat-ws (WebSocket)
```

## 跨服务调用

| 原调用链 | 方案 |
|---------|------|
| FriendService → MessageService.sendSystemMessage() | user-service → **RabbitMQ** → chat-service |
| GroupService → MessageService.sendSystemMessage() | user-service → **RabbitMQ** → chat-service |
| GroupService → ConversationService | user-service → **OpenFeign** → chat-service |
| MessageService → FileFeedService.recordFile() | chat-service → **RabbitMQ** → content-service |
| FileController → UserMapper (头像查询) | content-service → **OpenFeign** → user-service |
| Admin → 各 Mapper | admin 在 user-service 内，跨服务数据用 Feign 聚合 |

## Maven 模块结构

```
happyIM/
├── happyim-common/           # 精简：仅 JWT、ErrorCode、ApiResponse
├── happyim-api-contracts/    # NEW: Feign 接口 + 共享 DTO
├── services/
│   ├── user-service/         # Auth + User + Friend + Group + Admin
│   ├── chat-service/         # Message + Conversation + SensitiveWord
│   ├── content-service/      # Moment + Square + File + FileFeed
│   └── chat-ws/              # WebSocket（原 WS 模块）
├── gateway/                  # Spring Cloud Gateway
├── nginx/                    # nginx.conf
└── docker-compose.yml        # +Nacos
```

## 数据库拆分

| MySQL 表 | 归属 |
|----------|------|
| user, friend, friend_request, blacklist | user-service |
| group_chat, group_member, id_segment | user-service |
| admin_user | user-service |
| conversation, sensitive_word | chat-service |

| MongoDB 集合 | 归属 |
|-------------|------|
| messages, message_feed | chat-service |
| moments, square_posts, moment_notifications, square_notifications, user_summary | content-service |
| file_feed | content-service |

| Redis Key | 归属 |
|-----------|------|
| email:code:*, refresh:token:*, token:blacklist:* | user-service |
| chat:session:*, chat:sessions:*, msgid:spin:* | chat-service |
| online:user:*, router:user:* | chat-ws |
| square:leaderboard:* | content-service |

## 对比：拆分前 vs 拆分后

| | 拆分前 | 拆分后 |
|---|--------|--------|
| 业务模块 | 3 Maven 模块共进程 | 3 微服务 + 1 WS 独立部署 |
| 运行时组件 | 2（api + ws） | 6（nginx + gateway + 3 service + ws） |
| 数据库 | 共享 MySQL/MongoDB/Redis | 各服务独立数据源 |
| 扩容粒度 | 整体扩容 | chat-service 可独立扩容 |
| 故障隔离 | 一处崩溃全挂 | 文件服务挂了不影响聊天 |
| 新增复杂度 | - | Nacos + Feign + Gateway + Sentinel |

## 实施顺序

### Phase 1: 基础设施
1. Nacos 加入 docker-compose
2. 创建 `happyim-api-contracts`（共享 DTO + Feign 接口）
3. 精简 `happyim-common`
4. 创建 `gateway` 模块 + Nginx 配置

### Phase 2: 拆 content-service（最独立）
5. 拆出 Moment + Square + File → content-service
6. MongoDB social 集合归 content-service

### Phase 3: 拆 chat-service
7. 拆出 Message + Conversation → chat-service
8. chat-ws 适配新的 MQ 结构

### Phase 4: user-service 收尾
9. 剩余 Auth + User + Friend + Group + Admin → user-service  
10. 全链路联调

## 不做的事情

- 数据库分库分表 — 量不够，没必要
- K8s — docker-compose 够用
- 分布式事务框架 — 手工补偿 + 最终一致
- SkyWalking 链路追踪 — 后续加
