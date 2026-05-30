package com.happyim.content.service;

import com.happyim.contracts.feign.UserFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通过 OpenFeign 调用 user-service，unwrap ApiResponse {code, data}
 */
@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private final UserFeignClient feign;

    public UserServiceClient(UserFeignClient feign) {
        this.feign = feign;
    }

    public Map<String, Object> getUser(Long userId) {
        try {
            Map<String, Object> res = feign.getUserProfile(userId);
            if (isOk(res)) return data(res);
        } catch (Exception e) {
            log.warn("获取用户 {} 信息失败: {}", userId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> batchGetUsers(List<Long> userIds) {
        try {
            Map<String, Object> res = feign.batchGetUsers(userIds);
            if (isOk(res)) {
                Object d = res.get("data");
                if (d instanceof List) return (List<Map<String, Object>>) d;
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getFriends(Long userId) {
        try {
            Map<String, Object> res = feign.getUserFriends(userId);
            if (isOk(res)) {
                Object d = res.get("data");
                if (d instanceof List) return (List<Map<String, Object>>) d;
            }
        } catch (Exception e) {
            log.warn("获取用户 {} 好友列表失败: {}", userId, e.getMessage());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGroup(Long groupId) {
        try {
            Map<String, Object> res = feign.getGroupInfo(groupId);
            if (isOk(res)) return data(res);
        } catch (Exception e) {
            log.warn("获取群组 {} 信息失败: {}", groupId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    private boolean isOk(Map<String, Object> res) {
        return res != null && res.get("code") instanceof Integer code && code == 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> res) {
        return (Map<String, Object>) res.get("data");
    }
}
