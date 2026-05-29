package com.happyim.user.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.model.dto.*;
import com.happyim.common.mapper.GroupChatMapper;
import com.happyim.common.mapper.GroupMemberMapper;
import com.happyim.common.model.entity.GroupChat;
import com.happyim.common.model.entity.GroupMember;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.user.service.GroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupChatMapper groupChatMapper;
    private final JwtUtil jwtUtil;

    public GroupController(GroupService groupService, JwtUtil jwtUtil,
                           GroupMemberMapper groupMemberMapper, GroupChatMapper groupChatMapper) {
        this.groupService = groupService;
        this.groupChatMapper = groupChatMapper;
        this.jwtUtil = jwtUtil;
        this.groupMemberMapper = groupMemberMapper;
    }

    @PostMapping
    @LoginRequired
    public ApiResponse<GroupDetailVO> createGroup(@Valid @RequestBody CreateGroupRequest req, HttpServletRequest request) {
        Long userId = getUserId(request);
        return ApiResponse.success(groupService.createGroup(userId, req));
    }

    @GetMapping
    @LoginRequired
    public ApiResponse<List<GroupVO>> getMyGroups(HttpServletRequest request) {
        return ApiResponse.success(groupService.getMyGroups(getUserId(request)));
    }

    @GetMapping("/{groupId}/members/{userId}")
    @LoginRequired
    public ApiResponse<Map<String, Object>> getMember(@PathVariable Long groupId, @PathVariable Long userId) {
        GroupMember gm = groupMemberMapper.findByGroupAndUser(groupId, userId);
        if (gm == null) return ApiResponse.error(ErrorCode.NOT_FOUND, "该用户不在群中");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("userId", gm.getUserId());
        item.put("groupNickname", gm.getGroupNickname());
        item.put("role", gm.getRole());
        return ApiResponse.success(item);
    }

    @GetMapping("/{groupId}")
    @LoginRequired
    public ApiResponse<GroupDetailVO> getGroupDetail(@PathVariable Long groupId, HttpServletRequest request) {
        return ApiResponse.success(groupService.getGroupDetail(groupId, getUserId(request)));
    }

    @PutMapping("/{groupId}")
    @LoginRequired
    public ApiResponse<Void> updateGroup(@PathVariable Long groupId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        groupService.updateGroupInfo(groupId, getUserId(request), body);
        return ApiResponse.message("群信息已更新");
    }

    @PostMapping("/{groupId}/members")
    @LoginRequired
    public ApiResponse<Void> addMembers(@PathVariable Long groupId, @RequestBody Map<String, List<Object>> body,
                                         HttpServletRequest request) {
        groupService.addMembers(groupId, getUserId(request), body.get("userIds"));
        return ApiResponse.message("已邀请");
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @LoginRequired
    public ApiResponse<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId,
                                           HttpServletRequest request) {
        groupService.removeMember(groupId, getUserId(request), userId);
        return ApiResponse.message("已移除");
    }

    @PostMapping("/{groupId}/leave")
    @LoginRequired
    public ApiResponse<Void> leaveGroup(@PathVariable Long groupId, HttpServletRequest request) {
        groupService.leaveGroup(groupId, getUserId(request));
        return ApiResponse.message("已退出群聊");
    }

    @PostMapping("/{groupId}/dissolve")
    @LoginRequired
    public ApiResponse<Void> dissolveGroup(@PathVariable Long groupId, HttpServletRequest request) {
        groupService.dissolveGroup(groupId, getUserId(request));
        return ApiResponse.message("群已解散");
    }

    @PutMapping("/{groupId}/members/me/nickname")
    @LoginRequired
    public ApiResponse<Void> updateMyNickname(@PathVariable Long groupId, @RequestBody Map<String, String> body,
                                               HttpServletRequest request) {
        Long userId = getUserId(request);
        GroupMember gm = groupMemberMapper.findByGroupAndUser(groupId, userId);
        if (gm == null) throw new BizException(ErrorCode.FORBIDDEN, "你不在该群中");
        groupMemberMapper.updateGroupNickname(groupId, userId, body.get("groupNickname"));
        return ApiResponse.message("群昵称已更新");
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    @LoginRequired
    public ApiResponse<Void> setRole(@PathVariable Long groupId, @PathVariable Long userId,
                                      @RequestBody Map<String, Integer> body, HttpServletRequest request) {
        groupService.setRole(groupId, getUserId(request), userId, body.get("role"));
        return ApiResponse.message("角色已更新");
    }

    // 批量查询群信息（供前端 groupCache 使用）
    @PostMapping("/batch")
    public ApiResponse<List<Map<String, Object>>> batchGroups(@RequestBody List<Long> groupIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : groupIds) {
            GroupChat g = groupChatMapper.findById(id);
            if (g != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("groupId", g.getId());
                item.put("name", g.getName());
                String raw = g.getAvatarUrl();
                if (raw != null && !raw.isBlank() && !raw.startsWith("http"))
                    raw = "/api/files/download/" + raw.substring(raw.indexOf("/") + 1);
                item.put("avatarUrl", raw);
                item.put("memberCount", g.getMemberCount());
                result.add(item);
            }
        }
        return ApiResponse.success(result);
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtUtil.getUserId(header.substring(7));
        }
        throw new BizException(ErrorCode.NOT_LOGIN);
    }
}
