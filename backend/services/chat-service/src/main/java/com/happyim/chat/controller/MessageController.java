package com.happyim.chat.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.chat.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class MessageController {

    private final MessageService messageService;
    private final JwtUtil jwtUtil;

    public MessageController(MessageService messageService, JwtUtil jwtUtil) {
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/{conversationId}/messages")
    @LoginRequired
    public ApiResponse<Map<String, Object>> sendMessage(
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest req,
            HttpServletRequest request) {
        Long userId = getUserId(request);

        // 推断会话类型
        int convType = conversationId.startsWith("g_") ? 1 : 0;

        Map<String, Object> extra = new java.util.HashMap<>();
        if (req.getFileName() != null) extra.put("fileName", req.getFileName());
        if (req.getFileSize() != null) extra.put("fileSize", req.getFileSize());
        if (req.getDuration() != null) extra.put("duration", req.getDuration());
        if (req.getQuoteMessageId() != null) extra.put("quoteMessageId", req.getQuoteMessageId());
        if (req.getMentions() != null) extra.put("mentions", req.getMentions());

        Map<String, Object> result = messageService.sendMessage(
                userId, conversationId, convType,
                req.getContent(), req.getMessageType(), extra);

        return ApiResponse.success(result);
    }

    @GetMapping("/{conversationId}/messages")
    @LoginRequired
    public ApiResponse<Map<String, Object>> getMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        return ApiResponse.success(messageService.getMessages(userId, conversationId, offset, limit));
    }

    @GetMapping("/{conversationId}/messages/search")
    @LoginRequired
    public ApiResponse<Map<String, Object>> searchMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "30") int limit,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        return ApiResponse.success(messageService.searchMessages(userId, conversationId, type, keyword, since, offset, limit));
    }

    @GetMapping("")
    @LoginRequired
    public ApiResponse<List<Map<String, Object>>> getConversationList(HttpServletRequest request) {
        Long userId = getUserId(request);
        return ApiResponse.success(messageService.getConversationList(userId));
    }

    @PutMapping("/{conversationId}/read")
    @LoginRequired
    public ApiResponse<Void> markRead(@PathVariable String conversationId, HttpServletRequest request) {
        Long userId = getUserId(request);
        messageService.markRead(userId, conversationId);
        return ApiResponse.message("已标记已读");
    }

    @PutMapping("/{conversationId}/messages/{messageId}/recall")
    @LoginRequired
    public ApiResponse<Void> recallMessage(@PathVariable String conversationId, @PathVariable String messageId,
                                            HttpServletRequest request) {
        Long userId = getUserId(request);
        messageService.recallMessage(userId, messageId);
        return ApiResponse.message("消息已撤回");
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    @LoginRequired
    public ApiResponse<Void> deleteMessage(@PathVariable String conversationId, @PathVariable String messageId,
                                            HttpServletRequest request) {
        Long userId = getUserId(request);
        messageService.deleteMessage(userId, messageId);
        return ApiResponse.message("消息已删除");
    }

    @DeleteMapping("/{conversationId}")
    @LoginRequired
    public ApiResponse<Void> deleteConversation(@PathVariable String conversationId, HttpServletRequest request) {
        Long userId = getUserId(request);
        messageService.deleteConversation(userId, conversationId);
        return ApiResponse.message("会话已删除");
    }

    @DeleteMapping("/{conversationId}/messages")
    @LoginRequired
    public ApiResponse<Void> clearHistory(@PathVariable String conversationId, HttpServletRequest request) {
        Long userId = getUserId(request);
        messageService.clearHistory(userId, conversationId);
        return ApiResponse.message("聊天记录已清除");
    }

    @PutMapping("/{conversationId}/pin")
    @LoginRequired
    public ApiResponse<Void> pinConversation(@PathVariable String conversationId, HttpServletRequest request) {
        messageService.pinConversation(getUserId(request), conversationId, true);
        return ApiResponse.message("success");
    }

    @PutMapping("/{conversationId}/unpin")
    @LoginRequired
    public ApiResponse<Void> unpinConversation(@PathVariable String conversationId, HttpServletRequest request) {
        messageService.pinConversation(getUserId(request), conversationId, false);
        return ApiResponse.message("success");
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtUtil.getUserId(header.substring(7));
        }
        throw new BizException(ErrorCode.NOT_LOGIN);
    }

    public static class SendMessageRequest {
        @NotBlank(message = "消息内容不能为空")
        private String content;
        private String messageType = "text";
        private String fileName;
        private Long fileSize;
        private Integer duration;
        private String quoteMessageId;
        private List<Long> mentions;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getMessageType() { return messageType; }
        public void setMessageType(String messageType) { this.messageType = messageType; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public String getQuoteMessageId() { return quoteMessageId; }
        public void setQuoteMessageId(String quoteMessageId) { this.quoteMessageId = quoteMessageId; }
        public List<Long> getMentions() { return mentions; }
        public void setMentions(List<Long> mentions) { this.mentions = mentions; }
    }
}
