<!-- <img width="655" height="745" alt="image" src="https://github.com/user-attachments/assets/99053d3b-1b80-42aa-b85e-c79f472704a7" /> -->

<p align="center">
<img width="1024" height="450" alt="image" src="https://github.com/user-attachments/assets/024578d6-efac-495a-a129-f5efd01ba28a" />

</p>

# HappyIM — 不只是 Demo，这是一套能打的即时通讯系统

<p align="center">
  <img src="./截图/聊天页面，白色.png" width="100%" alt="亮色模式" />
</p>

<p align="center">
  <img src="./截图/聊天页面，黑色.png" width="100%" alt="暗色模式" />
</p>

<p align="center">
  <b>亮 / 暗双主题 · 仿微信交互 · 一套代码全栈吃透</b>
</p>

---

**HappyIM 远不止是一个「玩具项目」。** 它用最贴近生产环境的技术选型，完整实现了一款即时通讯应用该有的全部核心功能。单聊、群聊、朋友圈、广场社区、文件管理、离线消息推送——你每天在微信里用到的东西，这里全都有，而且你可以一行一行地读懂它是怎么实现的。

> 不用再对着抽象的系统设计图空想 IM 架构了。clone 下来，`docker-compose up`，你就能在本地跑起一个五脏俱全的 IM 系统。

---

## 为什么要看这个项目

市面上大部分 IM 教程止步于「一个聊天室 Demo」——没有离线消息、没有群聊、没有文件管理、没有朋友圈，面试官一问就穿帮。

**HappyIM 不一样：**

- 你写进简历的「分布式即时通讯系统」，它真的能做到每一帧截图都对得上
- 前后端完全分离，Vue 3 + Spring Boot，两个技术栈一起练
- 消息经过 RabbitMQ 路由，WebSocket 节点可横向扩展——这就是生产环境的骨架
- 5000+ 行 Java 后端代码、4000+ 行 Vue 前端代码，**足够有深度，又不会让你陷入烂尾**

---

## 先看效果

### 登录与注册

<p align="center">
  <img src="./截图/登录.png" width="100%" alt="登录" />
</p>

<p align="center">
  <img src="./截图/创建.png" width="100%" alt="注册" />
</p>

毛玻璃雨滴特效、邮箱验证码注册、注册时自选默认头像——**登录页就开始堆细节**，不糊弄。

### 聊天 — 整个系统的核心

<p align="center">
  <img src="./截图/聊天页面，白色.png" width="100%" alt="聊天主界面" />
</p>

- **WebSocket 长连接**，消息实时送达，不是轮询
- 支持文字、图片、视频、文件，消息类型全链路打通
- **离线消息自动推送**——关掉页面再打开，未读消息一条不落
- 消息状态追踪：发送中 → 已送达 → 已读

<p align="center">
  <img src="./截图/会话图片查看.png" width="100%" alt="图片预览" />
</p>

<p align="center">
  <img src="./截图/会话视频播放.png" width="100%" alt="视频播放" />
</p>

<p align="center">
  <img src="./截图/文件发送.png" width="100%" alt="文件发送" />
</p>

图片点击放大预览、视频内联播放、文件上传下载——**不是只发文字的聊天室，是真的能传文件的 IM。**

### 群聊

<p align="center">
  <img src="./截图/聊天-群聊抽屉.png" width="100%" alt="群聊" />
</p>

创建群聊、群成员管理、群公告、群内禁言——微信群的基础功能一个不少。

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

好友搜索 / 添加 / 备注 / 星标 / 黑名单，好友申请审批流，群聊创建与管理——**完整的好友关系链**，不是简单的「所有人互发消息」。

### 个人名片

<p align="center">
  <img src="./截图/会话个人名片.png" width="100%" alt="个人名片" />
</p>

点击头像弹出名片卡，查看对方详细资料——交互细节做到位。

### 朋友圈

<p align="center">
  <img src="./截图/朋友圈发布.png" width="100%" alt="发布朋友圈" />
</p>

<p align="center">
  <img src="./截图/朋友圈信箱.png" width="100%" alt="朋友圈通知" />
</p>

图文动态发布、视频动态、点赞、评论、回复评论、消息通知——**微信朋友圈的核心体验完整复刻。**

### 广场 — 公开社区

<p align="center">
  <img src="./截图/广场.png" width="100%" alt="广场" />
</p>

「朋友圈」是好友私域，「广场」是公开社区。今日活跃排行榜、发帖、点赞、评论——**一个项目里同时实现了私域社交和公域社区两套逻辑。**

### 文件管理

<p align="center">
  <img src="./截图/聊天文件管理.png" width="100%" alt="文件管理" />
</p>

所有聊天中收发过的文件集中管理，按类型筛选，随时下载。

### 个人设置

<p align="center">
  <img src="./截图/个人管理.png" width="100%" alt="个人资料" />
</p>

<p align="center">
  <img src="./截图/设置.png" width="100%" alt="设置" />
</p>

修改头像、昵称、签名、性别——资料编辑完即时生效，缓存同步更新。

---

## 技术架构

<p align="center">
  <b>前端 Vue 3</b> ← HTTP / WebSocket → <b>Spring Boot API</b> → <b>RabbitMQ</b> → <b>WebSocket 节点</b><br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↓&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↓&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↓<br/>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;MySQL&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Redis&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;MongoDB&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;MinIO
</p>

| 层级 | 技术栈 | 为什么选它 |
|------|--------|-----------|
| 前端框架 | Vue 3 + Composition API + Vite | 响应式 + HMR 极速开发体验 |
| UI 组件库 | Element Plus | 企业级组件，暗色主题开箱即用 |
| 后端框架 | Spring Boot 3 | Java 生态事实标准，新手友好 |
| 即时通信 | WebSocket (Spring) | 原生支持，无需额外服务 |
| 消息队列 | RabbitMQ | 解耦消息收发，WebSocket 节点可水平扩展 |
| 关系型数据库 | MySQL 8 | 用户、好友、群组等结构化数据 |
| 缓存 | Redis 7 | 验证码、Token 黑名单、在线状态 |
| 文档数据库 | MongoDB 7 | 离线消息持久化，消息体灵活 Schema |
| 对象存储 | MinIO | S3 兼容，图片/视频/文件统一存储 |
| 安全 | JWT 双 Token + BCrypt | 无状态鉴权，自动续期 |
| 容器化 | Docker Compose | 一键拉起全部中间件 |

**面试的时候，这张表背下来就够了。**

---

## 5 分钟跑起来

```bash
# 1. 拉代码
git clone https://github.com/ruiichi1228-spec/happyIM.git
cd happyIM

# 2. 启动中间件（MySQL + Redis + MongoDB + RabbitMQ + MinIO）
docker-compose up -d

# 3. 启动后端
cd backend
mvn clean package -DskipTests
java -jar happyim-api/target/happyim-api-1.0.0.jar

# 4. 新开终端，启动前端
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，注册两个账号，开始在两个窗口互发消息。

---

## 项目结构

```
happyIM/
├── frontend/                  # Vue 3 前端（4000+ 行）
│   └── src/
│       ├── pages/              # ChatPage / MomentsPage / SquarePage / LoginPage ...
│       ├── layouts/            # MainLayout（侧边栏导航）
│       ├── components/         # 通用组件
│       ├── utils/              # WebSocket / userCache / theme / request
│       └── router/             # 路由
├── backend/                    # Spring Boot 后端（5000+ 行）
│   ├── happyim-api/            # REST API，端口 8080
│   ├── happyim-ws/             # WebSocket 服务，端口 8081
│   └── happyim-common/         # 实体 / DTO / Mapper / 工具类
├── docs/                       # 设计文档（模块设计 + 完整架构报告）
│   └── final/                  # 架构终稿
├── 截图/                       # 项目截图
├── docker-compose.yml          # 一键启动开发环境
└── README.md
```

---

## 新手学习路线

| 天数 | 看什么 | 学到什么 |
|------|--------|---------|
| Day 1 | `docker-compose up` 跑起来，注册登录，发消息 | 感受完整流程，建立信心 |
| Day 2 | `LoginPage.vue` → `AuthController.java` → `AuthService.java` | 前后端如何协作完成注册登录、JWT 鉴权 |
| Day 3 | `ChatPage.vue` + `websocket.js` → `ChatWebSocketHandler.java` | WebSocket 连接建立、心跳保活、消息收发 |
| Day 4 | `MessageService.java` → RabbitMQ → `MessageConsumer.java` | 消息如何通过队列路由到正确的 WebSocket 节点 |
| Day 5 | `MomentsPage.vue` → `MomentController.java` | 朋友圈的发布、点赞、评论、通知完整链路 |
| Day 6 | `SquarePage.vue` → `SquareController.java` | 公开广场的帖子流 + 排行榜 |
| Day 7 | 自己动手加功能 | 消息撤回？表情包？语音消息？从这里开始 |


## Star 历史

如果这个项目帮到了你，点个 Star 让更多人看到——也让我知道有人在用，有动力继续维护。

---

## License

MIT — 拿去用，拿去改，拿去写在简历上。
