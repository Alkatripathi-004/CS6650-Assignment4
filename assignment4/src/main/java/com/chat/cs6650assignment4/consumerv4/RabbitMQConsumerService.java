package com.chat.cs6650assignment4.consumerv4;

import com.chat.cs6650assignment4.model.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RabbitMQConsumerService implements ChannelAwareMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConsumerService.class);

    private final Counter messagesProcessedCounter;
    private final Counter duplicateMessagesCounter;
    private final Counter failedMessagesCounter;

    private final ObjectMapper objectMapper;
    private final Set<String> processedMessageIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final BroadcastPublisherService broadcastPublisher;
    private final MessagePersistenceService persistenceService; // New dependency

    public RabbitMQConsumerService(ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry,
                                   BroadcastPublisherService broadcastPublisher,
                                   MessagePersistenceService persistenceService) {
        this.objectMapper = objectMapper;
        this.broadcastPublisher = broadcastPublisher;
        this.persistenceService = persistenceService; // Set dependency

        this.messagesProcessedCounter = Counter.builder("chat.messages.processed")
                .description("Total number of messages processed by the 'work' consumer")
                .register(meterRegistry);
        this.duplicateMessagesCounter = Counter.builder("chat.messages.duplicates")
                .register(meterRegistry);
        this.failedMessagesCounter = Counter.builder("chat.messages.failed")
                .register(meterRegistry);
    }

    @Override
    @Timed("chat.message.processing.time")
    public void onMessage(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        QueueMessage payload = objectMapper.readValue(message.getBody(), QueueMessage.class);

        if (!processedMessageIds.add(payload.getMessageId())) { // idempotency chk
            duplicateMessagesCounter.increment();
            channel.basicAck(tag, false);
            return;
        }

        try {
            broadcastPublisher.publishBroadcast(payload);

            persistenceService.persistAsync(payload);

            messagesProcessedCounter.increment();
            channel.basicAck(tag, false);

        } catch (Exception e) {
            logger.error("Error processing message {}. NACKing.", payload.getMessageId(), e);
            failedMessagesCounter.increment();
            channel.basicNack(tag, false, true);
        }
    }
}