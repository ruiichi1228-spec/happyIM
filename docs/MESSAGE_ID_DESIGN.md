# HappyIM 消息 ID 设计

## 方案：融云风格 80-bit + Redis 每会话序列生成器

### ID 结构（80 Bit）

```
┌──────────────┬──────────┬───────┬──────────────┐
│ 时间戳(42Bit)│ 自旋ID   │ 会话类│ 会话ID(22Bit)│
│              │(12Bit)   │ 型    │              │
│              │          │(4Bit) │              │
│              │          │       │              │
│ 2109年内有序 │ 同ms递增 │ 类型  │ 分库分表KEY  │
└──────────────┴──────────┴───────┴──────────────┘

编码: 32进制 (去掉了0,1,O,I), 19字符, 每4字符用 - 分隔
示例: BD8U-FCOJ-LDC5-L789
```

### Redis 序列生成器

为**每个会话**维护一个独立的序列计数器：

```
Key:    msgid:spin:{conversationId}
Type:   String (Integer)
操作:   INCR msgid:spin:p_user001_user002  → 返回 1, 2, 3, ...

TTL:    7天（会话不活跃后自动清理）
```

### 生成流程

```java
public String generateMessageId(String conversationId, int conversationType) {
    // 1. 时间戳 42bit
    long timestamp = System.currentTimeMillis();

    // 2. Redis 原子递增获取当前会话的序列号
    long spin = redis.incr("msgid:spin:" + conversationId);
    spin = spin & 0xFFF;  // 12bit, 0-4095, 同毫秒循环

    // 3. 会话类型 4bit (0-15)
    long typeBits = conversationType & 0xF;

    // 4. 会话 ID 哈希 22bit
    long convHash = hash(conversationId) & 0x3FFFFF;

    // 5. 拼接 80bit → 32进制编码 → 16字符 + 3个分隔符
    long high = (timestamp << 38) | (spin << 26) | (typeBits << 22) | (convHash >> 16);
    long low = convHash & 0xFFFF;  // 低16bit

    return encodeBase32(high, low);
}
```

### 为什么要每会话一个序列生成器？

| 方案 | 问题 |
|------|------|
| 全局计数器 (INCR msgid:seq) | 热点 key，分布式瓶颈 |
| 雪花算法本地生成 | 需要机器 ID 分配，部署复杂 |
| **每会话单独计数器** | 无热点、无依赖、天然按会话有序 |

同一会话内消息天然有序（序列递增），跨会话不需要有序（由前端按时间戳排序）。

## 消息 ID 即去重

消息 ID 本身就是业务主键，MongoDB `message_id` 字段有唯一索引。重复插入直接返回已存在的消息 ID，后端的去重逻辑非常简洁：

```java
public String sendMessage(String messageId, ...) {
    try {
        messagesCollection.insertOne(document);
    } catch (DuplicateKeyException e) {
        // 消息已存在，返回已有的 message_id
        return messageId;
    }
    // ... 后续推送逻辑
}
```

前端不需要再单独生成 dedup_id。发送流程简化：

```
前端发送消息
  ↓
后端接收 → 生成 messageId → MongoDB insertOne
  ├─ 成功 → 推送 + 返回 messageId 给发送方
  └─ 重复 → 幂等返回已有 messageId
```

但前端仍需要处理一种重复场景：**消息通过 WebSocket 实时收到一次，上线时游标拉取又收到一次**。这个通过前端维护已渲染的 message_id Set 来去重即可，不需要 UUID。

## 为什么不用 UUID / 拼接格式？

| 指标 | 拼接格式 | UUID | 雪花ID | **此方案** |
|------|--------|------|--------|----------|
| 有序性 | ⭐⭐ | ⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| B+树友好 | ⭐⭐ | ⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 可读性 | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐ | ⭐⭐⭐⭐ |
| 去重能力 | ⭐ | ⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 分布式无状态 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 热点争用 | 无 | 无 | 无 | 无（每会话一个 key） |

## 对比 RONGCLOUD_MESSAGE_ID.md 的调整

`RONGCLOUD_MESSAGE_ID.md` 中的 Java 实现使用了一个全局的 `AtomicInteger spinCounter`，这在分布式多实例部署时会冲突。本方案用 **Redis INCR 代替 JVM 内的 AtomicInteger**，每个会话一个 key，既解决了分布式的序列问题，又避免了全局热点。
