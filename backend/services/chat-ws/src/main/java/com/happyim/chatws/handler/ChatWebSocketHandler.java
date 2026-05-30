package com.happyim.chatws.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happyim.chatws.config.RabbitMQConfig;
import com.happyim.common.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // 当前会话从 session.attributes(HashMap) 移到独立 ConcurrentHashMap，避免并发读写死循环
    private final ConcurrentHashMap<Long, String> currentConversations = new ConcurrentHashMap<>();
    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final JwtUtil jwtUtil;
    private final RabbitMQConfig rabbitMQConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(RedisTemplate<String, String> redisTemplate,
                                RabbitTemplate rabbitTemplate, JwtUtil jwtUtil,
                                RabbitMQConfig rabbitMQConfig) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.jwtUtil = jwtUtil;
        this.rabbitMQConfig = rabbitMQConfig;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            Long userId = verifyToken(session);
            session.getAttributes().put("userId", userId);
            sessions.put(userId, session);

            redisTemplate.opsForValue().set("online:user:" + userId, "1", Duration.ofSeconds(60));
            redisTemplate.opsForValue().set("router:user:" + userId, rabbitMQConfig.routingKey(), Duration.ofSeconds(90));

            log.info("WS 连接建立: userId={}, route={}", userId, rabbitMQConfig.routingKey());
            try { rabbitTemplate.convertAndSend("happyim.exchange", "notify.online", Map.of("type", "friend_online", "userId", userId)); } catch(Exception ignored) {}
        } catch (Exception e) {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) return;

        Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
        String action = (String) msg.get("action");
        Map<String, Object> data = (Map<String, Object>) msg.get("data");

        switch (action) {
            case "ping" -> {
                redisTemplate.expire("online:user:" + userId, Duration.ofSeconds(60));
                redisTemplate.expire("router:user:" + userId, Duration.ofSeconds(90));
                sendMessageSafe(session, Map.of("action", "pong"));
            }
            case "enter_conversation" -> {
                String convId = (String) data.get("conversationId");
                currentConversations.put(userId, convId);
                log.info("用户 {} 进入会话 {}", userId, convId);
            }
            case "leave_conversation" -> {
                currentConversations.remove(userId);
            }
            case "mark_read" -> {
                String convId = (String) data.get("conversationId");
                if (convId != null) {
                    String hashKey = "chat:session:" + userId + ":" + convId;
                    redisTemplate.opsForHash().put(hashKey, "unread_count", "0");
                    String cursor = data.get("readCursor") instanceof String ? (String) data.get("readCursor") : null;
                    if (cursor != null && !cursor.isEmpty()) {
                        redisTemplate.opsForHash().put(hashKey, "read_cursor", cursor);
                    }
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            currentConversations.remove(userId);
            redisTemplate.delete("online:user:" + userId);
            // 不删路由键，只覆盖。断连期间消息仍可路由到活跃实例
            log.info("WS 连接断开: userId={}", userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WS 传输错误: {}", exception.getMessage());
    }

    // ===== 推送方法（供 Consumer 调用） =====

    public String getCurrentConversation(Long userId) {
        return currentConversations.get(userId);
    }

    public boolean isOnline(Long userId) {
        return sessions.containsKey(userId);
    }

    public Set<Long> getOnlineUserIds() {
        return new java.util.HashSet<>(sessions.keySet());
    }

    public void pushMessage(Long userId, Map<String, Object> payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            Map<String, Object> frame = Map.of("action", "message", "data", payload);
            sendMessageSafe(session, frame);
        }
    }

    public void pushNotification(Long userId, Map<String, Object> data) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            Map<String, Object> frame = Map.of("action", "new_message", "data", data);
            sendMessageSafe(session, frame);
        }
    }

    public void pushEvent(Long userId, Map<String, Object> data) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            Map<String, Object> frame = Map.of("action", "event", "data", data);
            sendMessageSafe(session, frame);
        }
    }

    /**
     * 线程安全发送：对 session 加锁，防止多个消费者线程并发写入导致帧损坏
     */
    private void sendMessageSafe(WebSocketSession session, Map<String, Object> frame) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                }
            }
        } catch (Exception e) {
            log.error("WS 推送失败: {}", e.getMessage());
        }
    }

    private Long verifyToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null) throw new IllegalArgumentException("no token");
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                String token = param.substring(6);
                return jwtUtil.getUserId(token);
            }
        }
        throw new IllegalArgumentException("no token");
    }
}
