package com.happyim.common.service;

import com.happyim.common.mapper.IdSegmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    private static final Logger log = LoggerFactory.getLogger(IdGenerator.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final IdSegmentMapper idSegmentMapper;

    public IdGenerator(RedisTemplate<String, String> redisTemplate, IdSegmentMapper idSegmentMapper) {
        this.redisTemplate = redisTemplate;
        this.idSegmentMapper = idSegmentMapper;
    }

    public Long nextUserId() {
        return nextId("user");
    }

    public Long nextGroupId() {
        return nextId("group");
    }

    private Long nextId(String bizTag) {
        String nextKey = "id:" + bizTag + ":next";
        Long id = redisTemplate.opsForValue().increment(nextKey);
        if (id == null) throw new RuntimeException("Redis INCR失败: " + bizTag);
        if (id % 1000 == 990) {
            replenishSegment(bizTag);
        }
        return id;
    }

    private synchronized void replenishSegment(String bizTag) {
        String lockKey = "id:segment:" + bizTag + ":lock";
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(5));
        if (Boolean.TRUE.equals(locked)) {
            try {
                int step = 1000;
                idSegmentMapper.incrementMaxId(bizTag, step);
                Long newMaxId = idSegmentMapper.getMaxId(bizTag);
                long newStart = newMaxId - step + 1;
                String nextKey = "id:" + bizTag + ":next";
                String currentStr = redisTemplate.opsForValue().get(nextKey);
                long current = currentStr != null ? Long.parseLong(currentStr) : newStart - 1;
                if (current < newStart) {
                    redisTemplate.opsForValue().set(nextKey, String.valueOf(newStart - 1));
                    log.info("{}号段回源成功: [{}, {}]", bizTag, newStart, newMaxId);
                }
            } finally {
                redisTemplate.delete(lockKey);
            }
        }
    }
}
