package com.happyim.user.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 用户信息本地缓存，减少 MySQL 查询
 */
@Component
public class UserCache {

    private static final Logger log = LoggerFactory.getLogger(UserCache.class);

    private final LoadingCache<Long, User> cache;

    public UserCache(UserMapper userMapper) {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @Override
                    public User load(Long userId) {
                        log.debug("缓存未命中，从DB加载: userId={}", userId);
                        return userMapper.findById(userId);
                    }
                });
    }

    public User get(Long userId) {
        try {
            return cache.get(userId);
        } catch (Exception e) {
            log.warn("缓存查询失败: userId={}", userId);
            return null;
        }
    }

    public void evict(Long userId) {
        cache.invalidate(userId);
    }

    public void put(User user) {
        if (user != null && user.getId() != null) {
            cache.put(user.getId(), user);
        }
    }
}
