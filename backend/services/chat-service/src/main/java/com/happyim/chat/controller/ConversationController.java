package com.happyim.chat.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.mapper.GroupChatMapper;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.chat.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final JwtUtil jwtUtil;
    private final GroupChatMapper groupChatMapper;

    public ConversationController(ConversationService conversationService, JwtUtil jwtUtil, GroupChatMapper groupChatMapper) {
        this.conversationService = conversationService;
        this.jwtUtil = jwtUtil;
        this.groupChatMapper = groupChatMapper;
    }

    @PostMapping("/private")
    @LoginRequired
    public ApiResponse<Map<String, String>> createPrivateConversation(@RequestBody Map<String, Long> body,
                                                                       HttpServletRequest request) {
        Long userId = getUserId(request);
        Long peerId = body.get("peerId");
        if (peerId == null) throw new BizException(ErrorCode.PARAM_ERROR);

        String convId = conversationService.createPrivateSession(userId, peerId);
        return ApiResponse.success(Map.of("conversationId", convId));
    }

    @PostMapping("/group/{groupId}")
    @LoginRequired
    public ApiResponse<Map<String, String>> ensureGroupConversation(@PathVariable Long groupId,
                                                                     HttpServletRequest request) {
        Long userId = getUserId(request);
        String convId = "g_" + groupId;
        // 查群信息填充 Redis 会话
        com.happyim.common.model.entity.GroupChat group = groupChatMapper.findById(groupId);
        String name = group != null ? group.getName() : "";
        conversationService.initGroupMemberSession(groupId, name, userId);
        return ApiResponse.success(Map.of("conversationId", convId));
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtUtil.getUserId(header.substring(7));
        }
        throw new BizException(ErrorCode.NOT_LOGIN);
    }
}
