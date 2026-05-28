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
 */
@FeignClient(name = "user-service", path = "/api/users")
public interface UserFeignClient {

    @GetMapping("/{id}/profile")
    Map<String, Object> getUserProfile(@PathVariable("id") Long userId);

    @PostMapping("/batch")
    List<Map<String, Object>> batchGetUsers(@RequestBody List<Long> userIds);
}
