package com.happyim.user.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.mapper.FriendMapper;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.Friend;
import com.happyim.common.model.entity.User;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.user.service.UserCache;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final FriendMapper friendMapper;
    private final UserCache userCache;

    public UserController(JwtUtil jwtUtil, UserMapper userMapper, FriendMapper friendMapper,
                          UserCache userCache) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.friendMapper = friendMapper;
        this.userCache = userCache;
    }

    private User getCachedUser(Long userId) {
        User user = userCache.get(userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);
        user.setPassword(null);
        String raw = user.getAvatarUrl();
        if (raw != null && !raw.isBlank() && !raw.startsWith("http")) {
            user.setAvatarUrl("/api/files/avatar/" + userId);
        }
        return user;
    }

    @GetMapping("/me")
    @LoginRequired
    public ApiResponse<User> getMyProfile(HttpServletRequest request) {
        Long userId = jwtUtil.getUserId(extractToken(request));
        return ApiResponse.success(getCachedUser(userId));
    }

    @GetMapping("/{id}/profile")
    public ApiResponse<User> getUserProfile(@PathVariable Long id) {
        return ApiResponse.success(getCachedUser(id));
    }

    @PostMapping("/batch")
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> batchUsers(@RequestBody List<Long> userIds, HttpServletRequest request) {
        Long selfId = jwtUtil.getUserId(extractToken(request));
        List<User> users = userMapper.findByIds(userIds);

        // 好友备注
        List<Friend> friends = friendMapper.findByUserIdAndFriendIds(selfId, userIds);
        Map<Long, String> remarkMap = new LinkedHashMap<>();
        Set<Long> friendSet = new HashSet<>();
        for (Friend f : friends) {
            remarkMap.put(f.getFriendId(), f.getRemark());
            friendSet.add(f.getFriendId());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", u.getId());
            item.put("nickname", u.getNickname());
            item.put("avatarUrl", resolveAvatar(u));
            item.put("gender", u.getGender());
            String remark = remarkMap.get(u.getId());
            item.put("remark", remark);
            item.put("displayName", remark != null ? remark : u.getNickname());
            item.put("isFriend", friendSet.contains(u.getId()));
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    @PutMapping("/me")
    @LoginRequired
    public ApiResponse<Void> updateProfile(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = jwtUtil.getUserId(extractToken(request));
        User user = userMapper.findById(userId);
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND);

        if (body.containsKey("nickname")) user.setNickname(body.get("nickname"));
        if (body.containsKey("avatarUrl")) user.setAvatarUrl(body.get("avatarUrl"));
        if (body.containsKey("gender")) user.setGender(Integer.valueOf(body.get("gender")));
        if (body.containsKey("signature")) user.setSignature(body.get("signature"));
        if (body.containsKey("description")) user.setDescription(body.get("description"));

        userMapper.updateProfile(user);
        userCache.evict(userId);  // 更新后淘汰缓存
        return ApiResponse.message("更新成功");
    }

    private String resolveAvatar(User u) {
        if (u == null) return null;
        String raw = u.getAvatarUrl();
        if (raw != null && !raw.isBlank() && !raw.startsWith("http")) return "/api/files/avatar/" + u.getId();
        return raw;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        return header.substring(7);
    }
}
