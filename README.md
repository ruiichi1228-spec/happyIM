# HappyIM - 分布式即时通信系统

仿微信的Web分布式即时通信项目，支持一对一实时聊天。

## 技术栈

- **前端**：Vue 3 + Vite + WebSocket
- **后端**：SpringBoot 3 + WebSocket + RabbitMQ
- **消息队列**：RabbitMQ（每个用户一个chat队列）
- **容器**：Docker + Docker Compose

## 快速启动

```bash
docker-compose up
```

访问前端：http://localhost:5173
后端API：http://localhost:8080

## 项目结构

```
happyIM/
├── backend/          # SpringBoot后端
├── frontend/         # Vue3前端
├── docker-compose.yml
└── README.md
```

## 功能

- [x] 用户登录
- [x] WebSocket连接管理
- [x] 一对一消息发送/接收
- [x] 消息队列持久化
- [x] 离线消息存储
