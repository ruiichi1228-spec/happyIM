package com.happyim.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * chat-service 对外暴露的 Feign 接口
 */
@FeignClient(name = "chat-service", path = "/api/conversations")
public interface ChatFeignClient {

    @PostMapping("/private")
    Object createPrivateConversation(@PathVariable Long userId1, @PathVariable Long userId2);
}
