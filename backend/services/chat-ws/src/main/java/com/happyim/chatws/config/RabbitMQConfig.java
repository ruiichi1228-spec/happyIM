package com.happyim.chatws.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${happyim.mq.exchange}")
    private String exchangeName;

    @Value("${happyim.mq.queue-prefix}")
    private String queuePrefix;

    @Value("${happyim.mq.routing-key-prefix}")
    private String routingKeyPrefix;

    @Value("${happyim.mq.instance-id}")
    private String instanceId;

    private String queueName() { return queuePrefix + instanceId; }
    public String routingKey() { return routingKeyPrefix + instanceId; }

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue wsQueue() {
        return new Queue(queueName(), true);
    }

    @Bean
    public Binding wsChatBinding(Queue wsQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(wsQueue).to(chatExchange).with(routingKey());
    }

    @Bean
    public Binding wsMomentBinding(Queue wsQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(wsQueue).to(chatExchange).with("notify.moment");
    }

    @Bean
    public Binding wsSquareBinding(Queue wsQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(wsQueue).to(chatExchange).with("notify.square");
    }

    @Bean
    public Binding wsFriendBinding(Queue wsQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(wsQueue).to(chatExchange).with("notify.friend");
    }

    @Bean
    public Binding wsOnlineBinding(Queue wsQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(wsQueue).to(chatExchange).with("notify.online");
    }

    @Bean
    public Binding wsAnnounceBinding(Queue wsQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(wsQueue).to(chatExchange).with("notify.announce");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}
