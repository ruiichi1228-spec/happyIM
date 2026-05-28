# 快速启动指南

## 前置要求

- Docker 20.10+
- Docker Compose 2.0+

## 启动服务

### 方式1: Docker Compose（推荐）

```bash
# 启动所有服务
docker-compose up

# 后台运行
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

### 方式2: 本地开发

**后端**：
```bash
cd backend
mvn clean package
mvn spring-boot:run
```

需要先启动RabbitMQ：
```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.12-management-alpine
```

**前端**：
```bash
cd frontend
npm install
npm run dev
```

## 访问地址

| 服务 | 地址 | 用户名/密码 |
|------|------|----------|
| 前端 | http://localhost:5173 | - |
| 后端API | http://localhost:8080 | - |
| RabbitMQ管理 | http://localhost:15672 | guest/guest |

## 测试一对一聊天

1. **打开两个浏览器标签页**

2. **第一个标签页**：
   - 访问 http://localhost:5173
   - 登录用户 `user1`
   - 选择 `user2` 进行聊天

3. **第二个标签页**：
   - 访问 http://localhost:5173
   - 登录用户 `user2`
   - 选择 `user1` 进行聊天

4. **发送消息测试**

## 项目结构

```
happyIM/
├── backend/                    # SpringBoot后端服务
│   ├── src/main/java/
│   │   └── com/happyim/chat/
│   │       ├── controller/     # API控制器
│   │       ├── handler/        # WebSocket处理器
│   │       ├── model/          # 数据模型
│   │       └── config/         # 配置类
│   ├── pom.xml                 # Maven配置
│   └── Dockerfile              # Docker构建文件
├── frontend/                   # Vue3前端应用
│   ├── src/
│   │   ├── App.vue             # 主组件
│   │   └── main.js             # 入口文件
│   ├── package.json
│   ├── vite.config.js          # Vite配置
│   ├── index.html
│   ├── nginx.conf              # Nginx配置
│   └── Dockerfile              # Docker构建文件
├── docker-compose.yml          # Docker Compose编排
└── README.md
```

## 核心功能

- ✅ 用户登录
- ✅ WebSocket连接管理
- ✅ 一对一消息发送
- ✅ 在线用户显示
- ✅ 消息历史显示
- ✅ RabbitMQ消息队列

## 消息流程

```
User1 WebSocket ─→ Backend ─→ RabbitMQ(queue:user2) ─→ User2 WebSocket
                   ↓
            Store in Queue (offline)
```

## 故障排查

### 后端连接RabbitMQ失败
- 检查RabbitMQ容器是否运行：`docker ps | grep rabbitmq`
- 查看日志：`docker-compose logs rabbitmq`

### 前端无法连接WebSocket
- 检查后端是否运行：`curl http://localhost:8080/api/users`
- 浏览器控制台查看WebSocket错误信息

### 消息无法接收
- 检查RabbitMQ队列：访问 http://localhost:15672（guest/guest）
- 验证两个用户是否都已连接

## 下一步扩展

- [ ] 数据库持久化（MySQL）
- [ ] 消息离线存储
- [ ] 群聊功能
- [ ] 消息加密
- [ ] 用户认证（JWT）
- [ ] 分布式部署（多个后端实例）
- [ ] Redis消息缓存
