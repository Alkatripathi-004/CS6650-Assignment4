package com.chat.cs6650assignment4.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableRabbit
public class RabbitMQConfig {
    public static final String TOPIC_EXCHANGE_NAME = "chat.exchange";
    public static final String FANOUT_EXCHANGE_NAME = "chat.broadcast.exchange";
    public static final String QUEUE_NAME_PREFIX = "room.";
    public static final String ROUTING_KEY_PREFIX = "room.";
    private static final int NUMBER_OF_ROOMS = 20;

    private static final int MESSAGE_TTL_MS = 360000;
    private static final int MAX_QUEUE_LENGTH = 5000;

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE_NAME);
    }

    @Bean
    public Declarables amqpDeclarables(TopicExchange topicExchange) {
        List<Declarable> declarables = new ArrayList<>();

        for (int i = 1; i <= NUMBER_OF_ROOMS; i++) {
            String queueName = QUEUE_NAME_PREFIX + i;
            String routingKey = ROUTING_KEY_PREFIX + i;
            Queue queue = QueueBuilder.durable(queueName)
                    .withArgument("x-message-ttl", MESSAGE_TTL_MS)
                    .withArgument("x-max-length", MAX_QUEUE_LENGTH)
                    .build();
            declarables.add(queue);

            Binding binding = BindingBuilder.bind(queue).to(topicExchange).with(routingKey);
            declarables.add(binding);
        }

        return new Declarables(declarables);
    }

    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(FANOUT_EXCHANGE_NAME);
    }

    @Bean
    public AnonymousQueue serverBroadcastQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}