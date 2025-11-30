package com.chat.cs6650assignment4.config;

import com.chat.cs6650assignment4.consumerv4.RabbitMQConsumerService;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DynamicConsumerConfig implements ApplicationRunner {

    private static final int NUMBER_OF_ROOMS = 20;

    @Value("${chat.consumer.thread-count}")
    private int threadCount;

    private final ConnectionFactory connectionFactory;
    private final RabbitMQConsumerService consumerService;
    private final RabbitAdmin rabbitAdmin;
    private final FanoutExchange fanoutExchange;
    private final AnonymousQueue serverBroadcastQueue;

    public DynamicConsumerConfig(ConnectionFactory connectionFactory,
                                 RabbitAdmin rabbitAdmin,
                                 RabbitMQConsumerService consumerService,
                                 FanoutExchange fanoutExchange,
                                 AnonymousQueue serverBroadcastQueue) {
        this.connectionFactory = connectionFactory;
        this.consumerService = consumerService;
        this.rabbitAdmin = rabbitAdmin;
        this.fanoutExchange = fanoutExchange;
        this.serverBroadcastQueue = serverBroadcastQueue;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("--- Starting Dynamic RabbitMQ Consumer Configuration ---");

        System.out.println("Manually declaring fanout exchange and binding...");

        rabbitAdmin.declareExchange(this.fanoutExchange);

        String queueName = this.serverBroadcastQueue.getName();

        var binding = BindingBuilder
                .bind(new org.springframework.amqp.core.Queue(queueName))
                .to(this.fanoutExchange);

        rabbitAdmin.declareBinding(binding);

        System.out.println("Binding of queue " + queueName + " to exchange " + fanoutExchange.getName() + " is complete.");

        Map<Integer, List<String>> threadQueueMap = new HashMap<>();
        for (int i = 1; i <= NUMBER_OF_ROOMS; i++) {
            int threadIndex = (i - 1) % threadCount;
            threadQueueMap.computeIfAbsent(threadIndex, k -> new ArrayList<>())
                    .add(RabbitMQConfig.QUEUE_NAME_PREFIX + i);
        }

        for (int i = 0; i < threadCount; i++) {
            List<String> queuesForThisThread = threadQueueMap.get(i);
            if (queuesForThisThread == null || queuesForThisThread.isEmpty()) {
                continue;
            }

            System.out.println("Creating and starting consumer thread " + i + " for queues: " + queuesForThisThread);

            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            container.setQueueNames(queuesForThisThread.toArray(new String[0]));
            container.setPrefetchCount(200);
            container.setMessageListener(consumerService);
            container.setBeanName("RoomConsumer-" + i);
            container.start();
        }

        System.out.println("--- Dynamic RabbitMQ Consumer Configuration Complete ---");
    }
}