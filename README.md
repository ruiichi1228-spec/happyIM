<p align="center">
  <img width="1024" height="450" alt="HappyIM Banner" src="https://github.com/user-attachments/assets/024578d6-efac-495a-a129-f5efd01ba28a" />
</p>

<h1 align="center">HappyIM</h1>
<h3 align="center">一个可用于生产环境的即时通讯系统</h3>

<p align="center">
  <img src="./截图/聊天页面，白色.png" width="100%" alt="亮色模式" />
</p>

<p align="center">
  <img src="./截图/聊天页面，黑色.png" width="100%" alt="暗色模式" />
</p>

<p align="center">
  <b>亮 / 暗双主题 · 仿微信交互 · 全栈参考实现</b>
</p>

---

HappyIM 是一个基于生产级架构模式构建的全栈即时通讯应用。它实现了现代 IM 平台完整的核心功能 —— 单聊、群聊、朋友圈、公开社区、文件管理与离线消息推送 —— 在整个技术栈上做到了清晰的职责分离。

> Clone 项目，执行 `docker-compose up`，即可在本地运行一个功能完善的 IM 系统。

---

## 版本

HappyIM 提供两种架构版本，可根据学习目标选择。

| | 单体架构 | 微服务架构 |
|---|---|---|
| **分支** | [`master`](https://github.com/ruiichi1228-spec/happyIM) | [`microservices`](https://github.com/ruiichi1228-spec/happyIM/tree/microservices) |
| **后端结构** | `happyim-api` (REST) + `happyim-ws` (WebSocket) + `happyim-common` (共享模块) | `gateway` + `user-service` + `chat-service` + `chat-ws` + `content-service` + `api-contracts` + `happyim-common` |
| **API 网关** | ✗ — 前端直连各服务 | ✓ — Spring Cloud Gateway 统一入口 |
| **服务间调用** | ✗ — 单进程直接调用 | ✓ — OpenFeign 声明式 RPC |
| **服务发现** | ✗ | Nacos |
| **复杂度** | 低，适合快速上手 | 中，适合学习分布式架构 |
| **前端** | 相同 | 相同 |

> `microservices` 分支是当前活跃开发的主线。`master` 分支保留了单体版本，适合希望从简单架构起步的开发者。

### 如何选择？

| 场景 | 推荐 |
|---|---|
| 刚接触 IM 或 Spring Boot | 从 [`master`](https://github.com/ruiichi1228-spec/happyIM) 开始，先把系统跑起来 |
| 已有单体经验，想学习微服务 | 使用 [`microservices`](https://github.com/ruiichi1228-spec/happyIM/tree/microservices) 分支，理解服务拆分、网关路由、服务间调用 |
| 准备系统设计面试 | 两个分支对照学习，深入理解两种架构的权衡取舍 |

---

## 功能

### 核心消息

<p align="center">
  <img src="./截图/聊天页面，白色.png" width="100%" alt="聊天界面" />
</p>

- **WebSocket 长连接**，消息实时送达（非轮询）
- 全媒体类型支持：文字、图片、视频、文件，端到端打通
- **离线消息推送** — 未送达的消息在接收者上线后自动推送
- 消息状态追踪：发送中 → 已送达 → 已读

<p align="center">
  <img src="./截图/会话图片查看.png" width="100%" alt="图片预览" />
</p>

<p align="center">
  <img src="./截图/会话视频播放.png" width="100%" alt="视频播放" />
</p>

<p align="center">
  <img src="./截图/文件发送.png" width="100%" alt="文件传输" />
</p>

### 群聊

<p align="center">
  <img src="./截图/聊天-群聊抽屉.png" width="100%" alt="群聊" />
</p>

群创建、群成员管理、群公告、群禁言。

### 联系人管理

<p align="center">
  <img src="./截图/联系人-私人管理.png" width="100%" alt="联系人" />
</p>

<p align="center">
  <img src="./截图/联系人-好友申请管理.png" width="100%" alt="好友申请" />
</p>

<p align="center">
  <img src="./截图/联系人-群聊管理.png" width="100%" alt="群聊管理" />
</p>

好友搜索 / 添加 / 备注 / 星标 / 黑名单；好友申请审批流；群组 CRUD。完整的好友关系链。

### 个人名片

<p align="center">
  <img src="./截图/会话个人名片.png" width="100%" alt="个人名片" />
</p>

点击头像弹出名片卡，查看详细资料。

### 朋友圈

<p align="center">
  <img src="./截图/朋友圈发布.png" width="100%" alt="发布朋友圈" />
</p>

<p align="center">
  <img src="./截图/朋友圈信箱.png" width="100%" alt="朋友圈通知" />
</p>

图文 / 视频动态发布，点赞、评论、二级回复、消息通知。

### 公开广场

<p align="center">
  <img src="./截图/广场.png" width="100%" alt="广场" />
</p>

公开社区空间，包含今日活跃排行榜、发帖、点赞、评论 —— 一套代码同时覆盖私域社交与公域社区两种逻辑。

### 文件管理

<p align="center">
  <img src="./截图/聊天文件管理.png" width="100%" alt="文件管理" />
</p>

所有会话中收发过的文件集中管理，按类型筛选。

### 个人设置

<p align="center">
  <img src="./截图/个人管理.png" width="100%" alt="个人资料" />
</p>

<p align="center">
  <img src="./截图/设置.png" width="100%" alt="设置" />
</p>

修改头像、昵称、签名、性别 —— 编辑即时生效，全系统缓存同步更新。

### 身份认证

<p align="center">
  <img src="./截图/登录.png" width="100%" alt="登录" />
</p>

<p align="center">
  <img src="./截图/创建.png" width="100%" alt="注册" />
</p>

邮箱验证码注册、注册时选择默认头像、毛玻璃雨滴动效。

---

## 技术架构

<p align="center">
  <b>Vue 3 SPA</b> ← HTTP / WebSocket → <b>Spring Cloud Gateway</b> → <b>微服务</b> → <b>消息队列</b> → <b>WebSocket 节点</b><br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↓&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↓&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↓<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;MySQL&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Redis&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;MongoDB&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;MinIO
</p>

| 层级 | 技术栈 | 选型理由 |
|---|---|---|
| 前端框架 | Vue 3 + Composition API + Vite | 响应式数据流，HMR 极速开发 |
| UI 组件库 | Element Plus | 企业级组件，暗色主题开箱即用 |
| 后端框架 | Spring Boot 3 + Spring Cloud Gateway | Java 生态事实标准，微服务路由 |
| API 网关 | Spring Cloud Gateway | 统一入口，按路由分发服务 |
| 实时通信 | WebSocket (Spring WebFlux) | 原生协议支持，不依赖外部 Broker |
| 消息队列 | RabbitMQ | 解耦消息收发，WebSocket 节点可水平扩展 |
| 关系型数据库 | MySQL 8 | 用户、好友、群组等结构化数据 |
| 缓存 | Redis 7 | 验证码、Token 黑名单、在线状态 |
| 文档数据库 | MongoDB 7 | 离线消息持久化，灵活的文档 Schema |
| 对象存储 | MinIO | S3 兼容，图片/视频/文件统一存储 |
| 认证 | JWT 双 Token + BCrypt | 无状态鉴权，自动续期 |
| 可观测性 | Prometheus + SkyWalking | 指标采集与分布式链路追踪 |
| 容器化 | Docker Compose | 一键拉起全部基础设施 |

---

## 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/ruiichi1228-spec/happyIM.git
cd happyIM

# 2. 启动基础设施（MySQL, Redis, MongoDB, RabbitMQ, MinIO）
docker-compose up -d

# 3. 构建并启动后端服务
cd backend
mvn clean package -DskipTests
java -jar services/user-service/target/user-service-*.jar &
java -jar services/chat-service/target/chat-service-*.jar &
java -jar services/chat-ws/target/chat-ws-*.jar &
java -jar services/content-service/target/content-service-*.jar &
java -jar gateway/target/gateway-*.jar &

# 4. 启动前端
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，注册两个账号，即可在两个窗口互发消息。

---

## 项目结构

```
happyIM/
├── frontend/                        # Vue 3 SPA（约 7,000 行）
│   └── src/
│       ├── pages/                   # ChatPage, MomentsPage, SquarePage, LoginPage ...
│       ├── layouts/                 # MainLayout（侧边栏导航）
│       ├── components/              # 通用组件
│       ├── utils/                   # WebSocket 客户端、userCache、主题切换、HTTP 请求封装
│       └── router/                  # Vue Router 路由配置
├── backend/                         # Spring Boot 微服务（约 7,400 行）
│   ├── gateway/                     # Spring Cloud Gateway（API 路由）
│   ├── services/
│   │   ├── user-service/            # 认证、用户资料、联系人
│   │   ├── chat-service/            # 消息持久化、会话逻辑
│   │   ├── chat-ws/                 # WebSocket 连接、实时推送
│   │   └── content-service/         # 朋友圈、广场、文件管理
│   ├── happyim-common/              # 共享实体、DTO、Mapper、工具类
│   └── api-contracts/               # 服务间接口定义
├── docs/                            # 设计文档
│   ├── COMPLETE_ARCHITECTURE.md     # 完整架构报告
│   ├── MESSAGE_PUSH_DESIGN.md       # 消息推送设计
│   ├── CONVERSATION_ID_AND_REDIS_DESIGN.md
│   └── ...
├── nginx/                           # Nginx 配置
├── prometheus/                      # Prometheus 配置
├── skywalking-agent/                # 分布式链路追踪探针
├── docker-compose.yml               # 本地开发基础设施
└── README.md
```

---

## 学习路线

| 天数 | 重点 | 学习内容 |
|---|---|---|
| Day 1 | 运行系统 | 环境搭建、注册登录、收发消息，建立整体认知 |
| Day 2 | 认证 | `LoginPage.vue` → Gateway → `AuthService` —— 注册登录前后端协作、JWT 双 Token 鉴权 |
| Day 3 | 实时消息 | `ChatPage.vue` + `websocket.js` → `ChatWebSocketHandler` —— WebSocket 连接建立、心跳保活、消息收发 |
| Day 4 | 消息路由 | `MessageService` → RabbitMQ → `MessageConsumer` —— 消息经队列路由至正确 WebSocket 节点 |
| Day 5 | 朋友圈 | `MomentsPage.vue` → `ContentService` —— 发布、点赞、评论、通知完整链路 |
| Day 6 | 广场 | `SquarePage.vue` → `ContentService` —— 公开帖子流与排行榜 |
| Day 7 | 扩展系统 | 消息撤回、表情包、语音消息 —— 应用已掌握的模式 |

---

## 关于项目

HappyIM 定位为分布式即时通讯系统架构的参考实现，面向学习和研究用途。项目包含约 7,400 行 Java 与约 7,000 行 TypeScript/Vue 代码，在前后端均有足够的深度，同时规模可控。

关键设计决策：

- **微服务拆分** —— 用户、消息、WebSocket、内容四个领域被拆分为独立服务，统一通过 API 网关接入
- **消息队列解耦** —— RabbitMQ 解耦消息生产与消费，使 WebSocket 节点可水平扩展
- **双数据库策略** —— MySQL 存储结构化关系数据（用户、群组、好友关系）；MongoDB 存储消息体，利用灵活的文档 Schema
- **无状态认证** —— JWT 双 Token（Access + Refresh）架构，支持自动续期

---

## Star 历史

如果这个项目对你有帮助，点个 Star 让更多人看到，也让维护者知道有人在用。

---

## 许可证

MIT — 可自由使用、修改和分发。
