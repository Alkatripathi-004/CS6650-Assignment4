package com.chat.cs6650assignment4.consumerv4;

import com.chat.cs6650assignment4.model.QueueMessage;
import com.google.common.collect.Lists;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class DynamoDBBatchWriter {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBBatchWriter.class);
    private static final String TABLE_NAME = "ChatMessages";
    private static final int DYNAMO_MAX_BATCH = 25;

    private static final int NUM_SHARDS = 5;
    private final Random random = new Random();

    private final DynamoDbClient dynamoDbClient;
    private final DlqService dlqService;
    private final Counter messagesWrittenCounter;

    public DynamoDBBatchWriter(DynamoDbClient dynamoDbClient, DlqService dlqService, MeterRegistry registry) {
        this.dynamoDbClient = dynamoDbClient;
        this.dlqService = dlqService;
        this.messagesWrittenCounter = Counter.builder("db.messages.written.total").register(registry);
    }

    @Timed(value = "db.write.batch")
    @CircuitBreaker(name = "dynamoDB", fallbackMethod = "fallbackWrite")
    @Retry(name = "dynamoDB")
    public void writeLogicalBatch(List<QueueMessage> messages) {
        messagesWrittenCounter.increment(messages.size());
        List<List<QueueMessage>> chunks = Lists.partition(messages, DYNAMO_MAX_BATCH);
        for (List<QueueMessage> chunk : chunks) {
            writePhysicalBatch(chunk);
        }
    }

    private void writePhysicalBatch(List<QueueMessage> chunk) {
        List<WriteRequest> writeRequests = new ArrayList<>();
        for (QueueMessage msg : chunk) {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("roomId", AttributeValue.builder().s(msg.getRoomId()).build());
            String sortKey = msg.getTimestamp() + "#" + msg.getMessageId();
            item.put("timestampSk", AttributeValue.builder().s(sortKey).build());

            String bucketId = String.valueOf(random.nextInt(NUM_SHARDS));
            item.put("bucketId", AttributeValue.builder().s(bucketId).build());

            item.put("messageId", AttributeValue.builder().s(msg.getMessageId()).build());
            item.put("userId", AttributeValue.builder().s(msg.getUserId()).build());
            item.put("username", AttributeValue.builder().s(msg.getUsername()).build());
            item.put("message", AttributeValue.builder().s(msg.getMessage()).build());
            item.put("timestamp", AttributeValue.builder().s(msg.getTimestamp()).build());

            writeRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(item).build()).build());
        }

        BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                .requestItems(Map.of(TABLE_NAME, writeRequests)).build();

        BatchWriteItemResponse response = dynamoDbClient.batchWriteItem(batchRequest);
        if (!response.unprocessedItems().isEmpty()) {
            logger.warn("Partial failure: {} items unprocessed", response.unprocessedItems().get(TABLE_NAME).size());
        }
    }

    public void fallbackWrite(List<QueueMessage> messages, Throwable t) {
        for (QueueMessage msg : messages) dlqService.sendToDlq(msg);
    }
}