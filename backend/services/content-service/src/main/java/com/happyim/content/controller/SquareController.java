package com.happyim.content.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.content.service.SquareService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/square")
public class SquareController {

    private final SquareService squareService;
    private final JwtUtil jwtUtil;

    public SquareController(SquareService squareService, JwtUtil jwtUtil) {
        this.squareService = squareService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/posts")
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> getPosts(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long filterUserId,
            HttpServletRequest request) {
        return ApiResponse.success(squareService.getPosts(getUserId(request), filterUserId, offset, limit));
    }

    @GetMapping("/posts/{id}")
    @LoginRequired
    public ApiResponse<Map<String, Object>> getById(@PathVariable String id, HttpServletRequest request) {
        Map<String, Object> m = squareService.getById(id, getUserId(request));
        if (m == null) return ApiResponse.error(ErrorCode.NOT_FOUND, "帖子不存在");
        return ApiResponse.success(m);
    }

    @PostMapping("/posts")
    @LoginRequired
    public ApiResponse<Map<String, Object>> publish(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = getUserId(request);
        String content = (String) body.get("content");
        String mediaUrls = (String) body.get("mediaUrls");
        String id = squareService.publish(userId, content, mediaUrls);
        return ApiResponse.success(Map.of("id", id));
    }

    @DeleteMapping("/posts/{id}")
    @LoginRequired
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        squareService.delete(id, getUserId(request));
        return ApiResponse.message("已删除");
    }

    @PostMapping("/posts/{id}/like")
    @LoginRequired
    public ApiResponse<Void> like(@PathVariable String id, HttpServletRequest request) {
        squareService.like(id, getUserId(request));
        return ApiResponse.message("已点赞");
    }

    @DeleteMapping("/posts/{id}/like")
    @LoginRequired
    public ApiResponse<Void> unlike(@PathVariable String id, HttpServletRequest request) {
        squareService.unlike(id, getUserId(request));
        return ApiResponse.message("已取消点赞");
    }

    @PostMapping("/posts/{id}/comments")
    @LoginRequired
    public ApiResponse<Void> comment(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        String content = (String) body.get("content");
        Object replyTo = body.get("replyToUserId");
        squareService.comment(id, getUserId(request), content, replyTo instanceof Number ? ((Number) replyTo).longValue() : null);
        return ApiResponse.message("评论成功");
    }

    @DeleteMapping("/posts/{id}/comments/{commentIndex}")
    @LoginRequired
    public ApiResponse<Void> deleteComment(@PathVariable String id, @PathVariable int commentIndex, HttpServletRequest request) {
        squareService.deleteComment(id, getUserId(request), commentIndex);
        return ApiResponse.message("评论已删除");
    }

    @GetMapping("/leaderboard")
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> getLeaderboard() {
        return ApiResponse.success(squareService.getLeaderboard());
    }

    @GetMapping("/notifications")
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> getNotifications(HttpServletRequest request) {
        return ApiResponse.success(squareService.getNotifications(getUserId(request)));
    }

    @GetMapping("/summary")
    @LoginRequired
    public ApiResponse<Map<String, Object>> getSummary(HttpServletRequest request) {
        return ApiResponse.success(squareService.getUserSummary(getUserId(request)));
    }

    @PutMapping("/notifications/read")
    @LoginRequired
    public ApiResponse<Void> markRead(HttpServletRequest request) {
        squareService.markAllRead(getUserId(request));
        return ApiResponse.message("已标记已读");
    }

    @DeleteMapping("/notifications")
    @LoginRequired
    public ApiResponse<Void> clearNotifications(HttpServletRequest request) {
        squareService.clearNotifications(getUserId(request));
        return ApiResponse.message("已清空");
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtUtil.getUserId(header.substring(7));
        }
        throw new BizException(ErrorCode.NOT_LOGIN);
    }
}
