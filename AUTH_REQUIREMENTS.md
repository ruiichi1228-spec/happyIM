# 用户注册与登录模块设计

## 1. 功能概述

| 功能 | 说明 |
|------|------|
| 注册 | 用户名 + 邮箱 + 密码 + 昵称 + 邮箱验证码，注册后跳转登录页 |
| 登录 | 邮箱 + 密码 + 邮箱验证码，返回 access_token + refresh_token |
| 修改密码 | 已登录用户修改密码，需邮箱验证码 |
| Token 刷新 | refresh_token 换新的 token 对，旧 token 立即失效 |
| 登出 | 撤销 refresh_token |

### 用户标识说明

| 字段 | 含义 | 谁可以看到 | 由谁设置 |
|------|------|-----------|---------|
| `id` | 系统内部ID，纯数字，号段生成 | 仅后端 | 系统自动 |
| `username` | 用户ID，5位以上字母数字下划线 | 所有人 | 用户注册时自行设置 |
| `nickname` | 展示名称 | 所有人 | 用户自行设置 |
| `email` | 邮箱 | 仅用户自己 | 用户设置 |

---

## 2. 数据库设计

### 2.1 user 表（MySQL）

```sql
CREATE TABLE `user` (
    `id`             BIGINT PRIMARY KEY COMMENT '系统内部ID，号段生成器分配，用户不可见',
    `username`       VARCHAR(64) NOT NULL COMMENT '用户ID，用户自行设置，5位以上字母数字下划线',
    `email`          VARCHAR(255) NOT NULL COMMENT '邮箱',
    `password`       VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    `nickname`       VARCHAR(100) NOT NULL COMMENT '展示昵称',
    `avatar_url`     VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `email_verified` TINYINT DEFAULT 0 COMMENT '邮箱是否已验证: 0=未验证, 1=已验证',
    `status`         TINYINT DEFAULT 1 COMMENT '账户状态: 0=禁用, 1=正常',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`  VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
    `created_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    INDEX `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 2.2 id_segment 表（MySQL）

```sql
CREATE TABLE `id_segment` (
    `biz_tag`    VARCHAR(32) PRIMARY KEY COMMENT '业务标识: user, group',
    `max_id`     BIGINT NOT NULL COMMENT '当前已分配的最大ID',
    `step`       INT NOT NULL DEFAULT 1000 COMMENT '每次预分配的号段长度',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ID号段表';

-- 初始化
INSERT INTO id_segment VALUES ('user', 20021228, 1000, NOW());
```

---

## 3. Redis 结构设计

### 3.1 邮箱验证码

```
email:code:{email}       → String  "123456"    TTL: 5分钟
email:send:cd:{email}    → String  "1"          TTL: 60秒
```

用途：
- `email:code:{email}` — 存储 6 位验证码，注册/登录/修改密码时校验
- `email:send:cd:{email}` — 发送冷却，防止 60 秒内重复发送

---

### 3.2 ID 号段

```
id:segment:user          → Hash  { start: "10001", end: "11000" }
id:user:next             → String (INCR 计数，从号段起点开始自增)
```

用途：
- 号段耗尽时从 MySQL `id_segment` 表回源，加载新区间
- Redis INCR 天然并发安全，各实例号段不重叠

---

### 3.3 Refresh Token

```
refresh:token:{jti}      → Hash
  - user_id:  "10001"
  - device:   "web"
  - ip:       "192.168.1.1"
  TTL: 7天 (与 refresh_token 过期时间一致)

refresh:user:{userId}    → Set { "jti-1", "jti-2", ... }
  TTL: 7天 (与最长活跃 token 一致)
```

用途：
- `refresh:token:{jti}` — 存储 refresh_token 对应的用户信息，登录时写入，登出时删除
- `refresh:user:{userId}` — 记录一个用户的所有活跃 refresh_token jti，用于查询多端登录状态

---

### 3.4 Access Token 黑名单

```
token:blacklist:{jti}    → String  "1"
  TTL: access_token 剩余有效时间
```

用途：
- 登出时把 access_token 的 jti 加入黑名单
- 拦截器校验时最先查这个，命中直接拒绝
- TTL 自动过期，无需手动清理

---

### 3.5 完整 Redis Key 一览

| Key | 类型 | TTL | 说明 |
|-----|------|-----|------|
| `email:code:{email}` | String | 5 min | 邮箱验证码 |
| `email:send:cd:{email}` | String | 60 s | 发送冷却 |
| `id:segment:user` | Hash | 永久 | 当前号段区间 |
| `id:user:next` | String | 永久 | 号段内 INCR 计数器 |
| `refresh:token:{jti}` | Hash | 7 d | refresh_token 会话信息 |
| `refresh:user:{userId}` | Set | 7 d | 用户所有活跃 token |
| `token:blacklist:{jti}` | String | 剩余时间 | access_token 黑名单 |

---

## 5. API 接口

### 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 错误码

| code | 说明 |
|------|------|
| 0 | 成功 |
| 10001 | 参数错误 |
| 10002 | 未登录 / Token 过期 |
| 10003 | 权限不足 |
| 10004 | 资源不存在 |
| 10005 | 重复操作 / 频繁请求 |
| 20001 | 邮箱已注册 |
| 20002 | 邮箱或密码错误 |
| 20003 | 验证码错误或已过期 |
| 20004 | 账户未验证邮箱 |
| 20005 | 账户已被禁用 |
| 20006 | Refresh Token 已过期或无效 |
| 20007 | 用户名已存在 |
| 20008 | 用户名格式不正确 (5位以上字母数字下划线) |
| 50001 | 服务器内部错误 |

---

### 5.1 发送邮箱验证码

```
POST /api/auth/send-code

Request:
{
  "email": "user@example.com"
}

Response (200):
{
  "code": 0,
  "message": "验证码已发送"
}
```

---

### 5.2 注册

```
POST /api/auth/register

Request:
{
  "username": "zhangsan",         // 5位以上，字母数字下划线
  "email": "user@example.com",
  "password": "Abc123456",
  "nickname": "张三",
  "code": "123456"               // 邮箱验证码
}

校验:
  - username 格式: ^[a-zA-Z0-9_]{5,64}$
  - username 未被注册
  - 邮箱格式
  - 邮箱未被注册
  - 密码长度 >= 8 位
  - 昵称 1-32 字符
  - 验证码正确且未过期

逻辑:
  1. 校验通过 → IdGenerator 生成内部 id
  2. BCrypt 加密密码
  3. INSERT INTO user
  4. email_verified = 1 (注册时已验证邮箱)
  5. 删除 Redis 验证码
  6. 返回成功

Response (201):
{
  "code": 0,
  "message": "注册成功，请登录",
  "data": {
    "id": 10001,
    "username": "zhangsan",
    "email": "user@example.com",
    "nickname": "张三"
  }
}

注: 注册成功不返回 token，用户需跳转登录页手动登录。
```

---

### 5.3 登录

```
POST /api/auth/login

Request:
{
  "email": "user@example.com",
  "password": "Abc123456",
  "code": "123456"               // 邮箱验证码
}

校验:
  - 邮箱已注册
  - 密码正确
  - 邮箱已验证 (email_verified = 1)
  - 账户状态正常 (status = 1)
  - 验证码正确且未过期

逻辑:
  1. 校验通过 → 生成 access_token + refresh_token
  2. 将 refresh_token 的 jti 写入 Redis:
     - HSET refresh:token:{jti} user_id={userId} device={device} ip={ip}
     - EXPIRE refresh:token:{jti} 604800
     - SADD refresh:user:{userId} {jti}
  3. UPDATE user SET last_login_time=NOW(), last_login_ip=?
  4. 删除 Redis 验证码

Response (200):
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "userId": 10001,
    "username": "zhangsan",
    "email": "user@example.com",
    "nickname": "张三",
    "avatarUrl": null,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 900,
    "refreshTokenExpiresIn": 604800
  }
}
```

---

### 5.4 刷新 Token

```
POST /api/auth/refresh-token

Request:
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}

逻辑:
  1. 解析 refresh_token → 获取 jti
  2. 查 Redis: EXISTS refresh:token:{jti}
  3. 不存在 → 返回 20006
  4. 删除旧记录: DEL refresh:token:{jti}, SREM refresh:user:{userId} {jti}
  5. 生成新 token 对 → 写入 Redis（同登录逻辑）
  6. 旧 access_token 的 jti 加入黑名单

Response (200):
{
  "code": 0,
  "message": "Token刷新成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresIn": 900,
    "refreshTokenExpiresIn": 604800
  }
}

Response (401):
{
  "code": 20006,
  "message": "Refresh Token已过期或无效"
}
```

---

### 5.5 登出

```
POST /api/auth/logout
Authorization: Bearer {accessToken}

Request:
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}

逻辑:
  1. 解析 access_token → 获取 jti 和 userId
  2. access_token jti 加入黑名单: SET token:blacklist:{jti} "1" EX {剩余秒数}
  3. 删除 refresh_token: DEL refresh:token:{refresh_jti}, SREM refresh:user:{userId} {refresh_jti}

Response (200):
{
  "code": 0,
  "message": "登出成功"
}
```

---

### 5.6 修改密码

```
PUT /api/auth/password
Authorization: Bearer {accessToken}

Request:
{
  "newPassword": "NewPass789",
  "code": "123456"               // 邮箱验证码
}

逻辑:
  1. 解析 access_token 获取 userId
  2. 查用户 → 获取 email
  3. 校验验证码正确且未过期
  4. BCrypt 加密新密码 → UPDATE user SET password = ?
  5. 删除 Redis 验证码

Response (200):
{
  "code": 0,
  "message": "密码修改成功，请重新登录"
}

Response (400):
{
  "code": 20003,
  "message": "验证码错误或已过期"
}
```

注：不需要旧密码，邮箱验证码已证明身份。修改密码成功后前端应清除 token 并跳转登录页。

---

## 6. JWT Token 设计

### Access Token

```
Header: { "alg": "HS256", "typ": "JWT" }

Payload:
{
  "sub": "10001",           // user_id
  "jti": "uuid-xxxx",       // 唯一标识，用于登出时加入黑名单
  "iat": 1716123456,        // 签发时间
  "exp": 1716124356         // 过期时间 (15分钟)
}

签名: HMAC-SHA256 (secret 来自配置文件)
```

### Refresh Token

```
Payload:
{
  "sub": "10001",
  "jti": "uuid-xxxx",       // 唯一标识，对应 user_session.token
  "iat": 1716123456,
  "exp": 1716728256         // 过期时间 (7天)
}
```

### 配置

```yaml
jwt:
  secret: happyim-jwt-secret-key-2024-must-be-at-least-256-bits-long-for-hs256
  access-token-expire: 900       # 15分钟
  refresh-token-expire: 604800   # 7天
```

---

## 7. 前端页面设计

### 7.1 登录页

```
┌──────────────────────────────┐
│                              │
│        [Logo / 图标]          │
│        HappyIM               │
│                              │
│  ┌────────────────────────┐  │
│  │  邮箱地址               │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  密码                   │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  邮箱验证码  │ 获取验证码 │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │       登 录             │  │
│  └────────────────────────┘  │
│                              │
│     注册账号                  │
│                              │
└──────────────────────────────┘
```

登录流程：
1. 用户输入邮箱和密码
2. 点击"获取验证码" → 调用 `POST /api/auth/send-code`
3. 输入收到的 6 位验证码
4. 点击"登录" → 调用 `POST /api/auth/login`
5. 成功 → token 对存入 localStorage → 跳转 `/chat`

### 7.2 注册页

```
┌──────────────────────────────┐
│                              │
│        [Logo / 图标]          │
│        创建账号               │
│                              │
│  ┌────────────────────────┐  │
│  │  用户名 (字母数字下划线) │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  昵称                   │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  邮箱地址               │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  邮箱验证码  │ 获取验证码 │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  密码                   │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  确认密码               │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │       注 册             │  │
│  └────────────────────────┘  │
│                              │
│     已有账号？返回登录        │
│                              │
└──────────────────────────────┘
```

注册流程：
1. 用户依次填写：用户名 → 昵称 → 邮箱
2. 点击"获取验证码" → 调用 `POST /api/auth/send-code`
3. 输入验证码 + 密码 + 确认密码
4. 点击"注册" → 调用 `POST /api/auth/register`
5. 成功 → 提示"注册成功，请登录" → 跳转 `/login`

### 7.3 修改密码页

```
┌──────────────────────────────┐
│                              │
│        [Logo / 图标]          │
│        修改密码               │
│                              │
│  ┌────────────────────────┐  │
│  │  邮箱验证码  │ 获取验证码 │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  新密码                 │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  确认新密码             │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │     确认修改             │  │
│  └────────────────────────┘  │
│                              │
└──────────────────────────────┘
```

页面路由：`/settings/password`，需登录后才能访问。
流程：
1. 系统自动发验证码到当前用户的注册邮箱
2. 输入验证码 + 新密码 + 确认新密码
3. 点击"确认修改" → 调用 `PUT /api/auth/password`
4. 成功 → 提示"密码修改成功，请重新登录" → 清除 token → 跳转 `/login`

### 7.4 前端 Token 管理

```
请求拦截器:
  - 非登录/注册请求 → 自动带上 Authorization: Bearer {accessToken}
  - 响应 10002 → 自动用 refreshToken 续期 → 重试原请求
  - 续期失败 → 清除 token → 跳转登录页

Token 存储:
  - localStorage:
    access_token
    refresh_token
    user_info (userId, email, nickname, avatarUrl)
```

---

## 8. 实现顺序

1. pom.xml 依赖 + application.yml 配置
2. 数据库表结构 SQL
3. common 包：ApiResponse, ErrorCode, BizException, GlobalExceptionHandler
4. Entity: User, UserSession, IdSegment
5. DTO: RegisterRequest, LoginRequest, TokenResponse, SendCodeRequest
6. MyBatis Mapper: UserMapper, UserSessionMapper, IdSegmentMapper
7. IdGenerator 工具类（Redis + MySQL 号段）
8. JwtUtil 工具类
9. MailService 邮件服务
10. AuthService 业务逻辑
11. AuthController 控制器
12. WebMvcConfig + AuthInterceptor 拦截器
13. 单元测试
14. 前端：登录页 + 注册页 + 修改密码页 + Token 管理
