package com.happyim.api.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.api.service.MomentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/moments")
public class MomentController {

    private final MomentService momentService;
    private final JwtUtil jwtUtil;

    public MomentController(MomentService momentService, JwtUtil jwtUtil) {
        this.momentService = momentService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> getTimeline(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long filterUserId,
            HttpServletRequest request) {
        return ApiResponse.success(momentService.getTimeline(getUserId(request), filterUserId, offset, limit));
    }

    @PostMapping
    @LoginRequired
    public ApiResponse<Map<String, Object>> publish(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = getUserId(request);
        String content = (String) body.get("content");
        String mediaUrls = (String) body.get("mediaUrls");
        String id = momentService.publish(userId, content, mediaUrls);
        return ApiResponse.success(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    @LoginRequired
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        momentService.delete(id, getUserId(request));
        return ApiResponse.message("已删除");
    }

    @PostMapping("/{id}/like")
    @LoginRequired
    public ApiResponse<Void> like(@PathVariable String id, HttpServletRequest request) {
        momentService.like(id, getUserId(request));
        return ApiResponse.message("已点赞");
    }

    @DeleteMapping("/{id}/like")
    @LoginRequired
    public ApiResponse<Void> unlike(@PathVariable String id, HttpServletRequest request) {
        momentService.unlike(id, getUserId(request));
        return ApiResponse.message("已取消点赞");
    }

    @PostMapping("/{id}/comments")
    @LoginRequired
    public ApiResponse<Void> comment(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        String content = (String) body.get("content");
        Object replyTo = body.get("replyToUserId");
        momentService.comment(id, getUserId(request), content, replyTo instanceof Number ? ((Number) replyTo).longValue() : null);
        return ApiResponse.message("评论成功");
    }

    @DeleteMapping("/{id}/comments/{commentIndex}")
    @LoginRequired
    public ApiResponse<Void> deleteComment(@PathVariable String id, @PathVariable int commentIndex, HttpServletRequest request) {
        momentService.deleteComment(id, getUserId(request), commentIndex);
        return ApiResponse.message("评论已删除");
    }

    @GetMapping("/notifications")
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> getNotifications(HttpServletRequest request) {
        return ApiResponse.success(momentService.getNotifications(getUserId(request)));
    }

    @GetMapping("/notifications/count")
    @LoginRequired
    public ApiResponse<Integer> getUnreadCount(HttpServletRequest request) {
        return ApiResponse.success(momentService.getUnreadCount(getUserId(request)));
    }

    @PutMapping("/notifications/read")
    @LoginRequired
    public ApiResponse<Void> markRead(HttpServletRequest request) {
        momentService.markAllRead(getUserId(request));
        return ApiResponse.message("已标记已读");
    }

    @DeleteMapping("/notifications")
    @LoginRequired
    public ApiResponse<Void> clearNotifications(HttpServletRequest request) {
        momentService.clearNotifications(getUserId(request));
        return ApiResponse.message("已清空");
    }

    @GetMapping("/summary")
    @LoginRequired
    public ApiResponse<Map<String, Object>> getSummary(HttpServletRequest request) {
        return ApiResponse.success(momentService.getUserSummary(getUserId(request)));
    }

    @GetMapping("/{id}")
    @LoginRequired
    public ApiResponse<Map<String, Object>> getById(@PathVariable String id, HttpServletRequest request) {
        Map<String, Object> m = momentService.getById(id, getUserId(request));
        if (m == null) return ApiResponse.error(ErrorCode.NOT_FOUND, "动态不存在");
        return ApiResponse.success(m);
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtUtil.getUserId(header.substring(7));
        }
        throw new BizException(ErrorCode.NOT_LOGIN);
    }
}
