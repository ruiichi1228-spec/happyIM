package com.happyim.chat.consumer;

import com.happyim.chat.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消费 user-service / group-service 发送的系统消息事件
 */
@Component
public class SystemMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(SystemMessageConsumer.class);
    private final MessageService messageService;

    public SystemMessageConsumer(MessageService messageService) {
        this.messageService = messageService;
    }

    @RabbitListener(queues = "happyim:system:message")
    public void onSystemMessage(Map<String, Object> event) {
        try {
            String type = (String) event.get("type");
            if (!"system_message".equals(type)) return;

            String conversationId = (String) event.get("conversationId");
            int convType = toInt(event.get("conversationType"));
            String content = (String) event.get("content");
            String subType = (String) event.get("subType");
            String msgType = (String) event.get("messageType");

            if (conversationId != null && content != null) {
                messageService.sendSystemMessage(conversationId, convType, content,
                        subType != null ? subType : "system",
                        msgType != null ? msgType : "system");
                log.info("系统消息已写入: conv={}, type={}", conversationId, subType);
            }
        } catch (Exception e) {
            log.error("处理系统消息失败: {}", e.getMessage(), e);
        }
    }

    private int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }
}
