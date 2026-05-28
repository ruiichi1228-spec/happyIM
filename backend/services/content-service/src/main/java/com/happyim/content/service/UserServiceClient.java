package com.happyim.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通过 REST 调用 user-service 获取用户和群组信息
 */
@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private static final String USER_SERVICE = "http://localhost:8101/api";

    private final RestTemplate restTemplate;

    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** 获取单个用户信息 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUser(Long userId) {
        try {
            Map<String, Object> res = restTemplate.getForObject(
                    USER_SERVICE + "/users/" + userId + "/profile", Map.class);
            if (res != null && res.get("code") instanceof Integer code && code == 0) {
                return (Map<String, Object>) res.get("data");
            }
        } catch (Exception e) {
            log.warn("获取用户 {} 信息失败: {}", userId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    /** 批量获取用户信息 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> batchGetUsers(List<Long> userIds) {
        try {
            Map<String, Object> res = restTemplate.postForObject(
                    USER_SERVICE + "/users/batch", userIds, Map.class);
            if (res != null && res.get("code") instanceof Integer code && code == 0) {
                return (List<Map<String, Object>>) res.get("data");
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /** 获取用户的好友 ID 列表 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getFriends(Long userId) {
        try {
            Map<String, Object> res = restTemplate.getForObject(
                    USER_SERVICE + "/friends?userId=" + userId, Map.class);
            if (res != null && res.get("code") instanceof Integer code && code == 0) {
                return (List<Map<String, Object>>) res.get("data");
            }
        } catch (Exception e) {
            log.warn("获取用户 {} 好友列表失败: {}", userId, e.getMessage());
        }
        return Collections.emptyList();
    }

    /** 获取群组信息 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getGroup(Long groupId) {
        try {
            Map<String, Object> res = restTemplate.getForObject(
                    USER_SERVICE + "/groups/" + groupId, Map.class);
            if (res != null && res.get("code") instanceof Integer code && code == 0) {
                return (Map<String, Object>) res.get("data");
            }
        } catch (Exception e) {
            log.warn("获取群组 {} 信息失败: {}", groupId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    /** 获取群成员列表 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getGroupMembers(Long groupId) {
        try {
            Map<String, Object> res = restTemplate.getForObject(
                    USER_SERVICE + "/groups/" + groupId, Map.class);
            if (res != null && res.get("code") instanceof Integer code && code == 0) {
                Map<String, Object> data = (Map<String, Object>) res.get("data");
                Object members = data.get("members");
                if (members instanceof List) {
                    return (List<Map<String, Object>>) members;
                }
            }
        } catch (Exception e) {
            log.warn("获取群 {} 成员列表失败: {}", groupId, e.getMessage());
        }
        return Collections.emptyList();
    }

    /** 更新用户头像 */
    @SuppressWarnings("unchecked")
    public void updateAvatar(Long userId, String avatarUrl) {
        try {
            Map<String, String> body = Map.of("avatarUrl", avatarUrl);
            restTemplate.put(USER_SERVICE + "/users/me?userId=" + userId, body);
        } catch (Exception e) {
            log.warn("更新用户 {} 头像失败: {}", userId, e.getMessage());
        }
    }
}
