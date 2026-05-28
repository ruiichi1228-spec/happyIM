# HappyIM 前端设计文档

## 1. 技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.x | Composition API 驱动 |
| Vite | 5.x | 构建工具 |
| Element Plus | 2.x | UI 组件库 |
| Vue Router | 4.x | SPA 路由 |
| Axios | 1.x | HTTP 请求 |
| unplugin-icons | - | Iconify 图标库集成 |

## 2. 路由设计

```
/                       重定向到 /login
/login                 登录页
/register               注册页
/reset-password         重置密码页
/chat                   聊天主页 (嵌套在 MainLayout)
/contacts               联系人页
/files                  文件管理页
/contacts/:userId       联系人详情 (同一组件)
```

朋友圈和广场不走路由，以弹窗 (Modal Overlay) 形式在 MainLayout 中打开。

## 3. 组件架构

```
App.vue
  └── RouterView
        ├── LoginPage.vue      (独立页面，无 MainLayout)
        ├── RegisterPage.vue   (独立页面)
        └── MainLayout.vue     (主布局)
              ├── 导航栏 (60px)
              │   ├── 头像 → 个人资料弹窗
              │   ├── 聊天 / 联系人 / 文件 / 朋友圈 / 广场 图标
              │   ├── 静音开关 / 设置齿轮
              │   └── WS 连接状态灯
              ├── RouterView (内容区)
              │   ├── ChatPage.vue
              │   │   ├── 会话列表
              │   │   ├── 聊天窗口 (消息气泡 + 输入区)
              │   │   └── 抽屉 (群信息 / 好友信息)
              │   ├── ContactsPage.vue
              │   │   ├── 好友列表 / 群列表
              │   │   └── 详情面板
              │   └── FilesPage.vue
              │       ├── 筛选侧边栏
              │       └── 文件列表
              ├── MomentsPage (弹窗覆盖层)
              └── SquarePage (弹窗覆盖层)
```

## 4. 核心工具模块

### 4.1 request.js — HTTP 请求封装

```javascript
// 自动携带 JWT
request.interceptors.request.use(config => {
  config.headers.Authorization = `Bearer ${accessToken}`
})

// 401 自动刷新 Token
request.interceptors.response.use(null, async error => {
  if (error.response.status === 401) {
    const newToken = await refreshAccessToken()
    // 重试原请求
  }
})
```

### 4.2 websocket.js — 共享 WebSocket 连接

模块级单例，所有组件共享一个 WebSocket 连接：

```
useWebSocket()
  ├── connect()         // 幂等连接，自动重连
  ├── send(data)        // 发送 JSON
  ├── onMessage(handler) // 注册消息处理器
  └── connected         // 响应式连接状态
```

MainLayout 负责连接生命周期，ChatPage/MomentsPage/SquarePage 通过 `onMessage()` 注册各自的事件处理器。

### 4.3 userCache.js — 用户信息本地缓存

```
useUserCache()
  ├── get(uid)          // 同步取缓存
  ├── set(uid, data)    // 写入缓存
  ├── setAll(list)      // 批量写入 (好友列表、群成员)
  ├── batchFetch(uids)  // 300ms 合并批量 HTTP 请求
  ├── fetchGroupMember(gid, uid) // 群成员昵称按需加载
  └── displayName(uid, gid) // remark > groupNickname > nickname
```

**设计理念**：
- 消息只传 `fromUserId`，不嵌入昵称头像
- 前端首次遇到用户 → `batchFetch` 批量请求
- 后续直接从模块级响应式 Map 读取
- 好友列表、群成员列表自动灌入缓存

### 4.4 sound.js — 通知提示音

使用 Web Audio API 合成四种不同音色：

| 事件 | 音色 |
|------|------|
| 新消息 | 双音 "叮咚" (sine 800Hz→1200Hz) |
| 好友请求 | 三角波 "叮咚" (triangle 600Hz→900Hz) |
| 朋友圈通知 | 三连升调 (sine 400→600→800Hz) |
| 广场通知 | 三连短音 (square 500→700→900Hz) |

## 5. 消息气泡设计

### 气泡类型

| 类型 | CSS 类 | 样式 |
|------|--------|------|
| 文字 | `.text-bubble` | 对方白底，自己绿底 |
| 图片 | `.media-card` | 白色卡片 + 圆角 + 阴影 |
| 视频 | `.media-card` | 同图片，视频播放器 |
| 文件 | `.file-card` | 彩色类型图标 + 文件名 + 大小 + 下载箭头 |
| 语音 | `.audio-card` | 胶囊形 + 播放按钮 + 5 根脉动条 |
| 名片 | `.card-msg` | 白色卡片 + 左侧绿边 + 头像 + 名称 |
| 位置 | `.loc-card` | 白色卡片 + 📍图标 + 坐标 + "查看地图" |
| 引用 | `.quote-banner` | 灰色底 + 左侧竖线，独立于气泡上方 |

### 引用消息

- 右键消息 → "引用"
- 输入区上方出现引用条 (发送者: 内容... ×)
- 发送消息时携带 `quoteMessageId`
- 后端查询被引用消息存储 `quoteContent` / `quoteMessageType`
- 点击引用条自动滚动到被引用消息位置（未加载则向上翻页）
- 非文字引用显示 `[图片]` / `[视频]` 等标签

## 6. 会话列表设计

- **排序**：ZSET score = 最后消息时间，`reverseRange` 最新在前
- **预览**：本地更新 `lastMsgContent` + `lastMsgTime`，不下拉刷新
- **类型标签**：`[文字]` 绿、`[图片]` 蓝、`[视频]` 紫、`[文件]` 橙
- **群聊标识**：蓝色方形「群」徽标
- **星标好友**：⭐ 前缀
- **备注优先**：私聊显示备注名
- **角色前缀**：群聊消息发送者名显示 👑群主 / 🔰管理

## 7. 录音功能

- 点击麦克风 → 弹出录音遮罩
- 脉动音波动画 + 倒计时 60s
- 到 0 自动停止并发送
- 取消按钮 → 丢弃录音
- 录制中取消标志位防止误上传

## 8. 主题切换

```
utils/theme.js
  isDark (ref) ← localStorage('happyim_theme')
  toggle()
  watch → document.documentElement.classList.toggle('dark')
```

暗色模式通过 `html.dark` 全局 CSS 选择器覆盖所有组件样式，使用五级色板：
- `--bg-nav` (最暗) → `--bg-sidebar` → `--bg-primary` → `--bg-secondary` → `--bg-card` → `--bg-input` (最亮)

## 9. 登录/注册页设计

- 模糊夜景图 + 毛玻璃雨滴效果
- 80 个随机大小雨滴，5-13 秒动画周期，从 -10% 流向 110%
- 登录卡片：毛玻璃背景 + 圆角阴影
- 湿润玻璃效果 (backdrop-filter blur)
