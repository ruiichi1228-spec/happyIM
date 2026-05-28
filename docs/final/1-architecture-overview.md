# HappyIM 系统架构总览

## 项目简介

HappyIM 是一个全栈即时通讯 Web 应用，支持用户注册登录、好友管理、群聊、私聊、实时消息推送、文件共享、朋友圈、广场社区等功能。前后端分离架构，后端采用 Spring Boot 多模块，前端采用 Vue 3。

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端框架 | Vue 3 (Composition API) | SPA，Vite 构建 |
| UI 组件库 | Element Plus | 统一组件风格 |
| 后端框架 | Spring Boot 3.x | Java 17+ |
| 数据库 | MySQL 8.0 | 用户、好友、群组等关系型数据 |
| 文档数据库 | MongoDB 7.0 | 消息、朋友圈、广场帖子 |
| 缓存 | Redis 7 | Session、游标、在线状态、排行榜 |
| 消息队列 | RabbitMQ 3.12 | 消息推送解耦 |
| 对象存储 | MinIO | 文件/头像/图片上传 |
| 容器化 | Docker Compose | 一键部署所有基础设施 |

## 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        Nginx (反向代理)                      │
├──────────────┬──────────────────┬────────────────────────────┤
│   Frontend   │   Backend API    │     WebSocket Server       │
│   Vue 3 SPA  │   Spring Boot   │     Spring Boot            │
│   Port 5173  │   Port 8080      │     Port 8081              │
│   (dev)      │                  │                            │
└──────────────┴────────┬─────────┴────────────┬───────────────┘
                        │                      │
            ┌───────────┼──────────────────────┼───────────┐
            │           │     RabbitMQ          │           │
            │   ┌───────┴──────┐               │           │
            │   │  chat.exchange │              │           │
            │   └───────┬──────┘               │           │
            │           │                      │           │
            │   ┌───────┴──────┐               │           │
            │   │   WS Queue    │              │           │
            │   └──────────────┘               │           │
            └───────────────────────────────────────────────┘
            ┌───────────────────────────────────────────────┐
            │              数据层                             │
            │  MySQL ←→ MongoDB ←→ Redis ←→ MinIO           │
            └───────────────────────────────────────────────┘
```

## 模块划分

### 后端模块

```
backend/
├── pom.xml                    # 父 POM，依赖管理
├── happyim-common/            # 共享模块
│   └── src/main/java/com/happyim/common/
│       ├── model/entity/      # 数据实体 (User, Friend, GroupChat...)
│       ├── model/dto/         # 数据传输对象
│       ├── mapper/            # MyBatis Mapper 接口 + XML
│       ├── util/              # ApiResponse, ErrorCode, BizException
│       ├── config/            # Redis, MinIO, MongoDB 配置
│       ├── security/          # JWT 工具, @LoginRequired 注解
│       └── service/           # 共享服务 (ID生成器, 消息ID生成器, 敏感词过滤)
├── happyim-api/               # HTTP API 服务 (Port 8080)
│   └── src/main/java/com/happyim/api/
│       ├── controller/        # REST 控制器
│       ├── service/           # 业务逻辑
│       └── config/            # RabbitMQ, 全局异常处理, 拦截器
└── happyim-ws/                # WebSocket 服务 (Port 8081)
    └── src/main/java/com/happyim/ws/
        ├── handler/           # WebSocket 连接处理器
        ├── consumer/          # RabbitMQ 消息消费者
        └── config/            # WebSocket, RabbitMQ 配置
```

### 前端模块

```
frontend/src/
├── main.js                    # 应用入口
├── App.vue                    # 根组件
├── router/index.js            # Vue Router 路由配置
├── layouts/MainLayout.vue     # 主布局 (导航栏 + 内容区)
├── pages/
│   ├── ChatPage.vue           # 聊天页面 (会话列表 + 聊天窗口)
│   ├── ContactsPage.vue       # 联系人页面 (好友/群聊管理)
│   ├── FilesPage.vue          # 文件管理页面
│   ├── LoginPage.vue          # 登录页面
│   ├── RegisterPage.vue       # 注册页面
│   ├── MomentsPage.vue        # 朋友圈弹窗
│   └── SquarePage.vue         # 广场弹窗
├── components/
│   └── WechatEmojiPicker.vue  # 表情选择器
├── utils/
│   ├── request.js             # Axios 封装 (JWT 拦截器)
│   ├── websocket.js           # 共享 WebSocket 连接
│   ├── userCache.js           # 用户信息本地缓存
│   ├── sound.js               # 通知提示音 (Web Audio API)
│   └── theme.js               # 深色/浅色主题切换
└── config/index.js            # API 地址配置
```

## 核心数据流

### 消息发送流程

```
Client A ──HTTP POST──▶ MessageController
                            │
                            ▼
                      MessageService.sendMessage()
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
         MongoDB        Redis Hash     RabbitMQ
       (messages)    (session 更新)   (chat.exchange)
                            │             │
                            │             ▼
                            │      MessageConsumer
                            │      (happyim-ws)
                            │             │
                            │             ▼
                            │    ChatWebSocketHandler
                            │    (ConcurrentHashMap)
                            │             │
                            │             ▼
                            │        Client B
                            │      (WebSocket Push)
                            │
                            ▼
                      Client A
                   (HTTP Response)
```

### 未读计数流程

```
发送消息
  │
  ├──▶ API: incrementUnread(receiver)    // HINCRBY 原子 +1
  │
  ▼
MQ 推送
  │
  ├──▶ Consumer: 如果用户正在看 → SET unread_count 0
  │              如果用户没在看 → 仅推送通知
  │
  ▼
用户打开会话
  │
  └──▶ PUT /conversations/{id}/read → SET unread_count 0 + 更新 read_cursor
```

## 关键设计决策

### 为什么用 MongoDB 存消息而不是 MySQL？

- 消息量巨大，水平扩展需求强烈，MongoDB 分片天然支持
- 消息 schema 灵活（文字、图片、文件、系统消息字段不同）
- 朋友圈/广场的嵌入式数组（likes, comments）在 MongoDB 中查询高效
- 避免了 MySQL 分库分表的运维复杂度

### 为什么消息推送用 RabbitMQ 而不是直接 WebSocket？

- **解耦**：HTTP API 服务和 WebSocket 服务独立部署、独立扩缩容
- **削峰**：高并发消息写入时 MQ 缓冲，避免 WS 连接被打满
- **可靠**：API 返回成功即表示消息已持久化（MongoDB），MQ 投递失败不影响消息可达性

### 为什么用号段模式生成用户 ID？

- 避免 UUID 作为主键的性能问题（B+Tree 分裂）
- 号段预分配（Redis + MySQL `id_segment` 表），本地缓存 1000 个 ID，99.9% 请求命中缓存
- 比雪花算法更适合需要连续数字 ID 的场景

### 为什么消息 ID 用 80-bit 自定义编码？

- 42-bit 时间戳 (毫秒) + 12-bit 序列号 + 4-bit 类型 + 22-bit CRC32
- 高位时间戳保证全局有序，字符串字典序 = 时间序
- CRC32 用于会话分片路由，不用于反查会话
