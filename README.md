# HappyIM — 从零搭建一个属于自己的即时通讯应用

HappyIM 是一个**开箱即用、适合新手学习**的分布式即时通讯（IM）系统。仿微信风格界面，一条命令即可在本地跑起完整的前后端服务。

**这不仅仅是一个 Demo** — 它从消息路由、WebSocket 长连接、离线消息推送到敏感词过滤，完整覆盖了 IM 系统的核心技术链路。如果你是 Java 初学者或正在准备面试项目，HappyIM 能帮你快速理解一个「像样的 IM」是怎么搭起来的。

## 为什么适合新手

- **一键启动**：`docker-compose up` 拉起全部中间件，无需手动安装 MySQL / Redis / RabbitMQ / MinIO
- **代码注释清晰**：Controller → Service → Mapper 三层结构，路径短，跟读无压力
- **前后端分离**：Vue 3 前端 + Spring Boot 后端，部署时完全独立，方便理解 HTTP + WebSocket 两种通信模式
- **覆盖面试高频技术点**：消息队列、WebSocket 长连接、JWT 鉴权、分布式 ID 生成、敏感词过滤
- **拿来就能改**：以 HappyIM 为骨架，你可以扩展表情包、语音消息、群机器人等进阶功能

## 功能一览

| 模块 | 已实现功能 |
|------|-----------|
| 用户系统 | 注册 / 登录 / 重置密码、邮箱验证码、JWT 双 Token 续期 |
| 即时通讯 | 单聊 / 群聊、WebSocket 长连接、消息已读未读 |
| 离线消息 | 用户离线时消息暂存，上线后自动推送 |
| 朋友圈 | 图文 / 视频动态发布、点赞、评论、消息通知 |
| 广场 | 公开帖子发布、排行榜、点赞评论 |
| 联系人 | 好友搜索 / 添加 / 备注 / 星标、黑名单 |
| 文件管理 | 文件上传下载、图片视频预览 |
| 安全过滤 | 敏感词过滤、XSS 防护 |
| 主题切换 | 亮色 / 暗色一键切换 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 (Composition API) · Vite · Element Plus · Axios |
| 后端 | Spring Boot 3 · Spring WebSocket · MyBatis |
| 数据库 | MySQL 8 · Redis 7 · MongoDB 7 |
| 消息队列 | RabbitMQ |
| 对象存储 | MinIO |
| 容器化 | Docker · Docker Compose |

## 5 分钟快速启动

### 前置要求

- Docker Desktop（或 Docker + Docker Compose）
- JDK 17+
- Node.js 18+

### 第一步：启动基础服务

```bash
docker-compose up -d
```

这会启动 MySQL、Redis、MongoDB、RabbitMQ、MinIO，端口映射如下：

| 服务 | 端口 |
|------|------|
| MySQL | 3308 |
| Redis | 6380 |
| MongoDB | 27019 |
| RabbitMQ | 5673（管理界面 15673） |
| MinIO | 9002（控制台 9003） |

### 第二步：启动后端

```bash
cd backend
mvn clean package -DskipTests
java -jar happyim-api/target/happyim-api-1.0.0.jar
```

API 服务运行在 `http://localhost:8080`

### 第三步：启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，注册账号即可开始使用。

## 项目结构

```
happyIM/
├── frontend/               # Vue 3 前端
│   └── src/
│       ├── pages/           # 页面组件（登录、聊天、朋友圈、广场...）
│       ├── components/      # 通用组件
│       ├── utils/           # 工具函数（WebSocket、userCache、主题...）
│       └── router/          # 路由配置
├── backend/                 # Spring Boot 后端
│   ├── happyim-api/         # REST API 模块（8080 端口）
│   ├── happyim-ws/          # WebSocket 模块（8081 端口）
│   └── happyim-common/      # 公共模块（实体、DTO、工具类）
├── docker-compose.yml       # 本地开发中间件
└── README.md
```

## 学习路线建议

1. **先跑起来**：按上面步骤启动，注册两个账号互发消息，感受完整流程
2. **读前端**：从 `LoginPage.vue` → `ChatPage.vue` 理解 WebSocket 连接和消息收发
3. **读后端**：从 `AuthController` 看注册登录 → `MessageController` 看消息发送 → `ChatWebSocketHandler` 看长连接管理
4. **扩展练习**：试着给消息加上「撤回」功能，或给朋友圈加上「转发」

## License

MIT
