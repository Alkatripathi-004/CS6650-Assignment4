package com.chat.cs6650assignment4.serverv4;

import com.chat.cs6650assignment4.config.RabbitMQConfig;
import com.chat.cs6650assignment4.model.QueueMessage;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.CircuitBreaker;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQProducerService {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @CircuitBreaker(
            include = {AmqpConnectException.class},
            maxAttempts = 3,
            openTimeout = 5000L,
            resetTimeout = 20000L
    )
    public void publishMessage(QueueMessage message) {
        String routingKey = RabbitMQConfig.ROUTING_KEY_PREFIX + message.getRoomId();
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOPIC_EXCHANGE_NAME, routingKey, message);
    }

    public void fallbackPublish(QueueMessage message, Throwable t) {
        System.err.println("Failed to publish message to RabbitMQ: " + t.getMessage());
    }

    @Recover
    public void recover(AmqpConnectException e, QueueMessage message) {
        System.err.println("Circuit breaker is open. Failed to publish message to RabbitMQ: " + message.getMessageId());
        System.err.println("Error: " + e.getMessage());
    }
}
