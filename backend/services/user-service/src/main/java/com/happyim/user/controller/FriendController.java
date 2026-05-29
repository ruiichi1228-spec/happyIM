package com.happyim.user.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.model.dto.*;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.user.service.FriendService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FriendController {

    private final FriendService friendService;
    private final JwtUtil jwtUtil;

    public FriendController(FriendService friendService, JwtUtil jwtUtil) {
        this.friendService = friendService;
        this.jwtUtil = jwtUtil;
    }

    // ==================== 搜索用户 ====================

    @GetMapping("/users/search")
    @LoginRequired
    public ApiResponse<List<UserSearchResult>> searchUsers(@RequestParam String keyword, HttpServletRequest request) {
        Long userId = getUserId(request);
        return ApiResponse.success(friendService.searchUsers(userId, keyword));
    }

    // ==================== 发送好友申请 ====================

    @PostMapping("/friends/request")
    @LoginRequired
    public ApiResponse<Void> sendRequest(@Valid @RequestBody FriendRequestDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        friendService.sendRequest(userId, dto.getToUserId(), dto.getMessage());
        return ApiResponse.message("好友申请已发送");
    }

    // ==================== 收到的申请 + 未读数量 ====================

    @GetMapping("/friends/requests")
    @LoginRequired
    public ApiResponse<Map<String, Object>> getRequests(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<FriendRequestVO> list = friendService.getReceivedRequests(userId);
        int pendingCount = (int) list.stream().filter(r -> r.getStatus() == 0).count();
        return ApiResponse.success(Map.of("list", list, "pendingCount", pendingCount));
    }

    // ==================== 同意申请 ====================

    @PostMapping("/friends/requests/{requestId}/accept")
    @LoginRequired
    public ApiResponse<Void> acceptRequest(@PathVariable Long requestId, HttpServletRequest request) {
        Long userId = getUserId(request);
        friendService.acceptRequest(requestId, userId);
        return ApiResponse.message("已添加为好友");
    }

    // ==================== 拒绝申请 ====================

    @PostMapping("/friends/requests/{requestId}/reject")
    @LoginRequired
    public ApiResponse<Void> rejectRequest(@PathVariable Long requestId, HttpServletRequest request) {
        Long userId = getUserId(request);
        friendService.rejectRequest(requestId, userId);
        return ApiResponse.message("已拒绝");
    }

    // ==================== 好友列表 ====================

    @GetMapping("/friends")
    @LoginRequired
    public ApiResponse<List<FriendVO>> getFriends(HttpServletRequest request) {
        Long userId = getUserId(request);
        return ApiResponse.success(friendService.getFriends(userId));
    }

    // 内部调用：根据 userId 查好友（服务间通信）
    @GetMapping("/users/{userId}/friends")
    public ApiResponse<List<FriendVO>> getUserFriends(@PathVariable Long userId) {
        return ApiResponse.success(friendService.getFriends(userId));
    }

    // ==================== 删除好友 ====================

    @DeleteMapping("/friends/{friendId}")
    @LoginRequired
    public ApiResponse<Void> deleteFriend(@PathVariable Long friendId, HttpServletRequest request) {
        Long userId = getUserId(request);
        friendService.deleteFriend(userId, friendId);
        return ApiResponse.message("已删除好友");
    }

    // ==================== 星标/取消星标 ====================

    @PutMapping("/friends/{friendId}/star")
    @LoginRequired
    public ApiResponse<Void> toggleStar(@PathVariable Long friendId, @RequestBody Map<String, Boolean> body,
                                         HttpServletRequest request) {
        Long userId = getUserId(request);
        boolean starred = body.getOrDefault("starred", false);
        friendService.toggleStar(userId, friendId, starred);
        return ApiResponse.message(starred ? "已设置星标" : "已取消星标");
    }

    @PutMapping("/friends/{friendId}/remark")
    @LoginRequired
    public ApiResponse<Void> updateRemark(@PathVariable Long friendId, @RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        Long userId = getUserId(request);
        friendService.updateRemark(userId, friendId, body.get("remark"));
        return ApiResponse.message("备注已更新");
    }

    // ==================== 拉黑 ====================

    @PostMapping("/friends/{blockUserId}/block")
    @LoginRequired
    public ApiResponse<Void> blockUser(@PathVariable Long blockUserId, HttpServletRequest request) {
        Long myId = getUserId(request);
        friendService.blockUser(myId, blockUserId);
        return ApiResponse.message("已拉黑");
    }

    // ==================== 取消拉黑 ====================

    @PostMapping("/friends/{blockUserId}/unblock")
    @LoginRequired
    public ApiResponse<Void> unblockUser(@PathVariable Long blockUserId, HttpServletRequest request) {
        Long myId = getUserId(request);
        friendService.unblockUser(myId, blockUserId);
        return ApiResponse.message("已取消拉黑");
    }

    // ==================== 黑名单列表 ====================

    @GetMapping("/friends/blacklist")
    @LoginRequired
    public ApiResponse<List<FriendVO>> getBlacklist(HttpServletRequest request) {
        Long userId = getUserId(request);
        return ApiResponse.success(friendService.getBlacklist(userId));
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        return jwtUtil.getUserId(header.substring(7));
    }
}
