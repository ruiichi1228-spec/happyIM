package com.happyim.chat.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

@Component
public class MessageIdGenerator {

    private static final String BASE32 = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final RedisTemplate<String, String> redisTemplate;

    public MessageIdGenerator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成 80-bit 消息 ID。
     *
     * 结构: timestamp(42) | spin(12) | type(4) | CRC32(22)
     *
     * CRC32 只做分片路由用，保证同一会话的消息落在同一分片。
     * 不用于反查会话 ID——会话 ID 是 MongoDB/Feed 里的独立字段。
     */
    public String generate(String conversationId, int conversationType) {
        long timestamp = System.currentTimeMillis() & ((1L << 42) - 1);

        long spin = incrementSpin(conversationId);

        long typeBits = conversationType & 0xF;

        long crc = crc22(conversationId);

        long id = (timestamp << 38)
                | (spin << 26)
                | (typeBits << 22)
                | crc;

        return encode(id);
    }

    private long incrementSpin(String conversationId) {
        String key = "msgid:spin:" + conversationId;
        Long val = redisTemplate.opsForValue().increment(key);
        return ((val != null ? val : 1) & 0xFFF);
    }

    private long crc22(String s) {
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return crc.getValue() & 0x3FFFFF;
    }

    private String encode(long id) {
        char[] chars = new char[16];
        for (int i = 15; i >= 0; i--) {
            chars[i] = BASE32.charAt((int) (id & 0x1F));
            id >>>= 5;
        }
        return new String(chars, 0, 4) + "-"
                + new String(chars, 4, 4) + "-"
                + new String(chars, 8, 4) + "-"
                + new String(chars, 12, 4);
    }
}
