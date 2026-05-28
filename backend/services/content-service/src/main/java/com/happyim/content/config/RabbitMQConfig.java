package com.happyim.content.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "happyim.exchange";
    public static final String QUEUE_FILE_RECORD = "happyim:file:record";
    public static final String ROUTE_FILE_RECORD = "file.record";

    @Bean
    public TopicExchange contentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue fileRecordQueue() {
        return QueueBuilder.durable(QUEUE_FILE_RECORD).build();
    }

    @Bean
    public Binding fileRecordBinding(Queue fileRecordQueue, TopicExchange contentExchange) {
        return BindingBuilder.bind(fileRecordQueue).to(contentExchange).with(ROUTE_FILE_RECORD);
    }
}
