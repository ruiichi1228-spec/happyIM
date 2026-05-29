package com.happyim.chat.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    public static final String EXCHANGE = "happyim.exchange";
    public static final String QUEUE_SYSTEM = "happyim:system:message";
    public static final String ROUTE_SYSTEM = "system.message";

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue systemMessageQueue() {
        return QueueBuilder.durable(QUEUE_SYSTEM).build();
    }

    @Bean
    public Binding systemMessageBinding(Queue systemMessageQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(systemMessageQueue).to(chatExchange).with(ROUTE_SYSTEM);
    }

    // ==================== 敏感词重载 ====================

    public static final String QUEUE_SENSITIVE = "happyim:sensitive:reload";
    public static final String ROUTE_SENSITIVE = "sensitive.reload";

    @Bean
    public Queue sensitiveReloadQueue() {
        return QueueBuilder.durable(QUEUE_SENSITIVE).build();
    }

    @Bean
    public Binding sensitiveReloadBinding(Queue sensitiveReloadQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(sensitiveReloadQueue).to(chatExchange).with(ROUTE_SENSITIVE);
    }
}
