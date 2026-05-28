package com.happyim.chatws.consumer;

import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.User;
import com.happyim.chatws.handler.ChatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);

    private final ChatWebSocketHandler wsHandler;
    private final UserMapper userMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public MessageConsumer(ChatWebSocketHandler wsHandler, UserMapper userMapper,
                           RedisTemplate<String, String> redisTemplate) {
        this.wsHandler = wsHandler;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = "${happyim.mq.queue}")
    public void onMessage(Map<String, Object> msg) {
        try {
            String type = (String) msg.get("type");
            if (type != null) {
                handleEvent(type, msg);
                return;
            }

            String messageId = (String) msg.get("messageId");
            String conversationId = (String) msg.get("conversationId");
            int convType = msg.get("conversationType") instanceof Integer ? (int) msg.get("conversationType") : 0;
            long fromUserId = msg.get("fromUserId") instanceof Integer ? (long) (int) msg.get("fromUserId") : (long) msg.get("fromUserId");
            String content = (String) msg.get("content");

            if (convType == 0) {
                handlePrivate(messageId, conversationId, fromUserId, msg);
            } else {
                handleGroup(messageId, conversationId, fromUserId, msg);
            }
        } catch (Exception e) {
            log.error("消费消息失败: {}", e.getMessage(), e);
        }
    }

    private void handleEvent(String type, Map<String, Object> msg) {
        Long targetUserId = toLong(msg.get("targetUserId"));
        if (targetUserId == null || !wsHandler.isOnline(targetUserId)) return;

        switch (type) {
            case "friend_notify" -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("type", "friend_notify");
                wsHandler.pushEvent(targetUserId, data);
            }
            case "moment_notify" -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("type", "moment_notify");
                wsHandler.pushEvent(targetUserId, data);
            }
            case "square_notify" -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("type", "square_notify");
                wsHandler.pushEvent(targetUserId, data);
            }
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }

    private void handlePrivate(String messageId, String convId, long fromUserId, Map<String, Object> msg) throws Exception {
        String[] parts = convId.substring(2).split("_");
        long a = Long.parseLong(parts[0]);
        long b = Long.parseLong(parts[1]);
        long receiverId = (fromUserId == a) ? b : a;
        pushToUser(receiverId, convId, messageId, fromUserId, msg);
    }

    @SuppressWarnings("unchecked")
    private void handleGroup(String messageId, String convId, long fromUserId, Map<String, Object> msg) throws Exception {
        List<Integer> memberIds = (List<Integer>) msg.get("members");
        if (memberIds == null) return;
        for (int uid : memberIds) {
            if ((long) uid == fromUserId) continue;
            pushToUser((long) uid, convId, messageId, fromUserId, msg);
        }
    }

    private void pushToUser(Long userId, String convId, String messageId,
                             long fromUserId, Map<String, Object> msg) throws Exception {
        if (!wsHandler.isOnline(userId)) return;

        String content = (String) msg.get("content");
        String viewing = wsHandler.getCurrentConversation(userId);
        if (convId.equals(viewing)) {
            // 正在查看 → 推送完整消息，归零未读，推进游标
            Map<String, Object> payload = new LinkedHashMap<>(msg);
            wsHandler.pushMessage(userId, payload);
            String hashKey = "chat:session:" + userId + ":" + convId;
            redisTemplate.opsForHash().put(hashKey, "unread_count", "0");
            redisTemplate.opsForHash().put(hashKey, "read_cursor", messageId);
        } else {
            // 未查看 → 推送通知（未读数 API 端已 +1，此处不再加）
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("conversationId", convId);
            data.put("preview", content != null && content.length() > 30 ? content.substring(0, 30) : content);
            data.put("senderName", fromUserId > 0 ? getSenderName(fromUserId) : "系统");
            data.put("messageType", msg.get("messageType") != null ? msg.get("messageType").toString() : "text");
            wsHandler.pushNotification(userId, data);
        }
    }

    private String getSenderName(Long userId) {
        User user = userMapper.findById(userId);
        return user != null ? (user.getNickname() != null ? user.getNickname() : user.getUsername()) : "";
    }
}
