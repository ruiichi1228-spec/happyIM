package com.happyim.content.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.content.service.FileFeedService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/file-feed")
public class FileFeedController {

    private final FileFeedService fileFeedService;
    private final JwtUtil jwtUtil;

    public FileFeedController(FileFeedService fileFeedService, JwtUtil jwtUtil) {
        this.fileFeedService = fileFeedService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/feed")
    @LoginRequired
    public ApiResponse<Map<String, Object>> getFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Long senderId,
            @RequestParam(required = false) String conversationId,
            HttpServletRequest request) {
        return ApiResponse.success(fileFeedService.getFeed(getUserId(request), page, size, fileType, senderId, conversationId));
    }

    @GetMapping("/feed/senders")
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> getSenders(HttpServletRequest request) {
        return ApiResponse.success(fileFeedService.getSenders(getUserId(request)));
    }

    @GetMapping("/feed/conversations")
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> getConversations(HttpServletRequest request) {
        return ApiResponse.success(fileFeedService.getConversations(getUserId(request)));
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtUtil.getUserId(header.substring(7));
        }
        throw new BizException(ErrorCode.NOT_LOGIN);
    }
}
