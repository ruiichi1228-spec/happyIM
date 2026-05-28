# HappyIM 部署指南

## 环境要求

| 软件 | 版本 | 端口 |
|------|------|------|
| JDK | 17+ | - |
| Node.js | 18+ | - |
| Docker | 24+ | - |
| Docker Compose | v2 | - |

## 快速启动 (Docker Compose)

### 1. 启动基础设施

```bash
docker compose up -d
```

启动的服务：

| 容器 | 端口 | 账号/密码 |
|------|------|----------|
| MySQL 8.0 | 3308:3306 | root / root |
| MongoDB 7.0 | 27019:27017 | 无认证 |
| Redis 7 | 6380:6379 | 无密码 |
| RabbitMQ 3.12 | 5673:5672 / 15673:15672 | guest / guest |
| MinIO | 9002:9000 / 9003:9001 | minioadmin / minioadmin |

### 2. 初始化数据库

```bash
# MySQL 建表
docker exec -i happyim-mysql mysql -uroot -proot happyim < backend/happyim-common/src/main/resources/schema.sql

# 如果已有数据库，手动加列
docker exec -i happyim-mysql mysql -uroot -proot happyim -e "
  ALTER TABLE user ADD COLUMN gender TINYINT DEFAULT 0;
  ALTER TABLE user ADD COLUMN signature VARCHAR(60);
  ALTER TABLE user ADD COLUMN description VARCHAR(250);
"

# MinIO 创建 Bucket
docker exec happyim-minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker exec happyim-minio mc mb local/happyim
```

### 3. 启动后端

```bash
# API 服务 (Port 8080)
cd backend/happyim-api
mvn spring-boot:run

# WebSocket 服务 (Port 8081)
cd backend/happyim-ws
mvn spring-boot:run
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器运行在 `http://localhost:5173`，自动代理 API 请求到 `localhost:8080`。

### 5. 生产构建

```bash
# 前端构建
cd frontend && npm run build
# 产物在 frontend/dist，用 Nginx 部署

# 后端打包
cd backend && mvn package -DskipTests
# 产物在 happyim-api/target/happyim-api.jar
# java -jar happyim-api.jar --spring.profiles.active=prod
```

## 配置文件

### 后端 application.yml (关键配置)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3308/happyim
    username: root
    password: root
  data:
    mongodb:
      uri: mongodb://localhost:27019/happyim
    redis:
      host: localhost
      port: 6380
  rabbitmq:
    host: localhost
    port: 5673
    username: guest
    password: guest

minio:
  endpoint: http://localhost:9002
  access-key: minioadmin
  secret-key: minioadmin

happyim:
  jwt:
    secret: happyim-jwt-secret-key-2024-must-be-at-least-256-bits-long-for-hs256
    access-token-ttl: 900000       # 15分钟
    refresh-token-ttl: 604800000   # 7天
  mq:
    exchange: chat.exchange
    queue: chat:ws:ws-1
    routing-key: ws.ws-1
```

### 前端 config/index.js

```javascript
export const API_BASE_URL = 'http://localhost:8080/api'
```

## 多实例扩展

### API 服务水平扩展

```
Nginx (upstream) → api-1:8080, api-2:8080, api-3:8080
```

无状态服务，直接加实例即可。

### WebSocket 服务扩展

当前 WS 服务使用内存 `ConcurrentHashMap` 管理连接，不直接支持多实例。改造方案：

```
方案 1: Redis Pub/Sub 广播
  WS-1 推送 → Redis Pub/Sub → WS-2, WS-3 收到 → 推送给本地连接的用户

方案 2: 每个用户路由到固定 WS 实例
  Redis router:user:{id} → "ws-1"
  API → 查 Redis → 投递到对应 WS 实例的 RabbitMQ Queue
```

建议采用方案 2，MQ Queue 按 WS 实例名动态绑定。

## 常见问题

### MongoDB 连不上？

检查 Docker 是否启动：
```bash
docker ps | grep happyim-mongodb
```

### MinIO 上传失败？

初始化 MinIO Bucket：
```bash
docker exec happyim-minio mc mb local/happyim
```

### MySQL 表不存在？

运行 schema.sql 初始化：
```bash
docker exec -i happyim-mysql mysql -uroot -proot happyim < schema.sql
```

### 邮件验证码发不了？

在 `application.yml` 中配置 SMTP：
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
```
