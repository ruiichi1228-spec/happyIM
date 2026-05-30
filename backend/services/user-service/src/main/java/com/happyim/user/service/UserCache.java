package com.happyim.user.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 用户信息本地缓存，批量回源 MySQL，避免逐条查询
 */
@Component
public class UserCache {

    private static final Logger log = LoggerFactory.getLogger(UserCache.class);

    private final Cache<Long, User> cache;
    private final UserMapper userMapper;

    public UserCache(UserMapper userMapper) {
        this.userMapper = userMapper;
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public User get(Long userId) {
        User u = cache.getIfPresent(userId);
        if (u == null) {
            u = userMapper.findById(userId);
            if (u != null) cache.put(userId, u);
        }
        return u;
    }

    /** 批量获取，缓存未命中时一次性查 DB */
    public Map<Long, User> batchGet(Collection<Long> userIds) {
        Map<Long, User> result = new HashMap<>();
        List<Long> missed = new ArrayList<>();

        for (Long id : userIds) {
            if (id == null) continue;
            User u = cache.getIfPresent(id);
            if (u != null) {
                result.put(id, u);
            } else {
                missed.add(id);
            }
        }

        if (!missed.isEmpty()) {
            List<User> loaded = userMapper.findByIds(missed);
            for (User u : loaded) {
                cache.put(u.getId(), u);
                result.put(u.getId(), u);
            }
        }

        return result;
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
