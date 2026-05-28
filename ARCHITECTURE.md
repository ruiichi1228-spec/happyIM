# 分布式路由表架构说明

## 系统架构

```
┌─────────────┐     WebSocket      ┌──────────────┐
│  Frontend   │────────────────────→│   Backend    │
│  (Vue 3)    │←────────────────────│ (SpringBoot) │
└─────────────┘                     └──────┬───────┘
                                           │
                                           ↓
                                    ┌─────────────┐
                                    │  RabbitMQ   │
                                    │   Queues    │
                                    └─────────────┘
```

## 消息队列设计

### 队列命名规则

- **User Queue**: `chat_${userId}` 
  - 每个用户对应一个队列
  - 用于存储该用户的离线消息
  - 用户上线时消费该队列的消息

- **Broadcast Queue**: `broadcast_queue`
  - 用于系统级广播（预留）

### 消息路由流程

```
1. User1 发送消息给 User2
   ├─ WebSocket 连接 → Backend
   ├─ 消息入队: chat_user2
   └─ 如果 User2 在线
      └─ 直接通过 WebSocket 发送

2. User2 离线时
   └─ 消息持久化在 chat_user2 队列中
   
3. User2 上线时
   ├─ 建立 WebSocket 连接
   ├─ 后端监听其队列消息
   └─ 消费所有未读消息并推送
```

## 核心组件

### 后端 (Backend)

#### 1. WebSocketHandler - 连接管理
```java
功能:
- 建立/关闭 WebSocket 连接
- 维护用户在线状态（Session Map）
- 接收/发送消息
- 提取用户身份信息
```

#### 2. ChatMessage - 消息模型
```java
属性:
- fromUser: 发送者
- toUser: 接收者
- content: 消息内容
- timestamp: 时间戳
- type: 消息类型
```

#### 3. RabbitMQConfig - 队列配置
```java
- 为每个用户动态创建队列
- 绑定消息交换机
```

#### 4. UserController - API端点
```java
POST /api/login       - 用户登录
GET /api/users        - 获取用户列表
```

### 前端 (Frontend)

#### 1. 登录模块
- 输入用户ID
- 调用 `/api/login` 验证
- 建立 WebSocket 连接

#### 2. 聊天模块
- 用户列表（侧边栏）
- 消息显示区
- 消息输入框

#### 3. WebSocket 客户端
- 连接URL: `ws://backend:8080/chat?userId={userId}`
- 消息格式: JSON
- 心跳保活（自动）

## 分布式部署考虑

### 当前单实例架构
```
User1 ──┐
User2 ──┼─→ [Backend 1 + RabbitMQ Local]
User3 ──┘
```

### 多实例架构扩展方向
```
       ┌──→ [Backend 1] ──┐
User ──┤                  ├─→ [RabbitMQ Cluster]
       └──→ [Backend 2] ──┘
       
关键技术:
- 负载均衡器 (Nginx/LB)
- 共享 RabbitMQ 集群
- 分布式会话 (Redis)
- 路由表同步
```

### 路由表设计

```
分布式路由表结构:
{
  "user1": "backend-1:8080",
  "user2": "backend-1:8080",
  "user3": "backend-2:8080",
  ...
}

维护方式:
1. 中央注册中心 (Nacos/Consul)
   └─ 后端启动时注册
   └─ 用户上线时更新路由
   
2. 用户查询流程
   ├─ 查询路由表 → 获取用户所在后端
   ├─ 转发消息到该后端
   └─ 后端通过 WebSocket 直接发送
```

## 消息发送流程详解

### 在线场景
```
User1 (Backend-1)              User2 (Backend-2)
     │                              │
     ├─ send message ─→ Query Router Table
                       → Backend-2 location
                       ├─ Send to Backend-2
                       └─ Backend-2 直接发送给 User2
```

### 离线场景
```
User1 (Backend-1)              User2 (Offline)
     │                              │
     ├─ send message ─→ Query Router Table
                       → User2 最后位置 or default queue
                       ├─ Send to RabbitMQ(chat_user2)
                       └─ 消息入队等待 User2 上线
                       
User2 上线时:
     ├─ 连接到某个 Backend
     ├─ 后端消费 chat_user2 队列
     └─ 推送所有离线消息
```

## 配置参数

### RabbitMQ 配置
```yaml
spring.rabbitmq.host: rabbitmq
spring.rabbitmq.port: 5672
spring.rabbitmq.username: guest
spring.rabbitmq.password: guest
spring.rabbitmq.virtual-host: /
```

### WebSocket 配置
```
连接路径: /chat
协议: ws/wss
心跳: 自动（浏览器级别）
超时: 连接保持
```

## 性能考虑

| 指标 | 值 | 说明 |
|------|-----|------|
| 并发连接 | 10000+ | 单后端实例 |
| 消息吞吐 | 1000+ msg/s | 取决于 RabbitMQ |
| 消息延迟 | <100ms | 本地网络 |
| 队列持久化 | 支持 | RabbitMQ 配置 |

## 监控指标

```
后端:
- WebSocket 活跃连接数
- 消息发送/接收速率
- 消息处理延迟

RabbitMQ:
- 队列深度
- 消息积压
- 消费者数量
```
