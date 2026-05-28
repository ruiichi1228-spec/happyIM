package com.happyim.chat.consumer;

import com.happyim.common.service.SensitiveWordFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消费 user-service 的敏感词变更事件，重载过滤器
 */
@Component
public class SensitiveReloadConsumer {

    private static final Logger log = LoggerFactory.getLogger(SensitiveReloadConsumer.class);
    private final SensitiveWordFilter sensitiveWordFilter;

    public SensitiveReloadConsumer(SensitiveWordFilter sensitiveWordFilter) {
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @RabbitListener(queues = "happyim:sensitive:reload")
    public void onReload(Map<String, Object> event) {
        try {
            String type = (String) event.get("type");
            if (!"sensitive_reload".equals(type)) return;

            sensitiveWordFilter.reload();
            log.info("敏感词过滤器已重载");
        } catch (Exception e) {
            log.error("重载敏感词失败: {}", e.getMessage(), e);
        }
    }
}
