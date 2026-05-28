package com.happyim.content.consumer;

import com.happyim.content.service.FileFeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消费 chat-service 发送的文件记录事件，写入 file_feed
 */
@Component
public class FileRecordConsumer {

    private static final Logger log = LoggerFactory.getLogger(FileRecordConsumer.class);
    private final FileFeedService fileFeedService;

    public FileRecordConsumer(FileFeedService fileFeedService) {
        this.fileFeedService = fileFeedService;
    }

    @RabbitListener(queues = "happyim:file:record")
    public void onFileRecord(Map<String, Object> event) {
        try {
            String type = (String) event.get("type");
            if (!"file_record".equals(type)) return;

            Long userId = toLong(event.get("userId"));
            Long senderId = toLong(event.get("senderId"));
            String conversationId = (String) event.get("conversationId");
            int convType = toInt(event.get("conversationType"));
            String fileName = (String) event.get("fileName");
            long fileSize = toLong(event.get("fileSize"));
            String fileUrl = (String) event.get("fileUrl");
            String messageType = (String) event.get("messageType");
            String messageId = (String) event.get("messageId");

            fileFeedService.recordFile(userId, senderId, conversationId, convType,
                    fileName, fileSize, fileUrl, messageType, messageId);
            log.info("文件记录已写入: {}, conv={}", fileName, conversationId);
        } catch (Exception e) {
            log.error("处理文件记录失败: {}", e.getMessage(), e);
        }
    }

    private Long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    private int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }
}
