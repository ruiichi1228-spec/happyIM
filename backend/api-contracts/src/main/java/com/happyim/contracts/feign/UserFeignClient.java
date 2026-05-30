package com.happyim.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * user-service 对外暴露的 Feign 接口
 * 所有方法返回 ApiResponse 的 JSON 结构 {code, data}，由调用方 unwrap
 */
@FeignClient(name = "user-service", path = "/api")
public interface UserFeignClient {

    @GetMapping("/users/{id}/profile")
    Map<String, Object> getUserProfile(@PathVariable("id") Long userId);

    @PostMapping("/users/batch")
    Map<String, Object> batchGetUsers(@RequestBody List<Long> userIds);

    @GetMapping("/users/{userId}/friends")
    Map<String, Object> getUserFriends(@PathVariable Long userId);

    @GetMapping("/groups/{groupId}")
    Map<String, Object> getGroupInfo(@PathVariable Long groupId);
}
