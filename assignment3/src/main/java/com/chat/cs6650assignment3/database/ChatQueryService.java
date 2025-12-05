package com.chat.cs6650assignment3.database;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ChatQueryService {

    private final DynamoDbClient dynamoDbClient;
    private static final String TABLE_NAME = "ChatMessages";
    private static final String GSI_USER = "UserIndex";
    private static final String GSI_TIME = "TimeIndex";
    private static final int NUM_SHARDS = 5;

    public ChatQueryService(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public List<Map<String, String>> getRoomHistory(String roomId, String start, String end) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":pk", AttributeValue.builder().s(roomId).build());
        eav.put(":start", AttributeValue.builder().s(start).build());
        eav.put(":end", AttributeValue.builder().s(end).build());

        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("roomId = :pk AND timestampSk BETWEEN :start AND :end")
                .expressionAttributeValues(eav)
                .scanIndexForward(true)
                .limit(100)
                .build();
        return executeQuery(request);
    }

    public List<Map<String, String>> getUserHistory(String userId, String start, String end) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":pk", AttributeValue.builder().s(userId).build());
        StringBuilder keyCondition = new StringBuilder("userId = :pk");

        if (start != null && end != null) {
            keyCondition.append(" AND timestampSk BETWEEN :start AND :end");
            eav.put(":start", AttributeValue.builder().s(start).build());
            eav.put(":end", AttributeValue.builder().s(end).build());
        }

        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName(GSI_USER)
                .keyConditionExpression(keyCondition.toString())
                .expressionAttributeValues(eav)
                .limit(100)
                .build();
        return executeQuery(request);
    }

    public List<Map<String, String>> getRoomsForUser(String userId) {
        List<Map<String, String>> history = getUserHistory(userId, null, null);

        Map<String, String> roomLastSeen = new HashMap<>();
        for (Map<String, String> msg : history) {
            String rId = msg.get("roomId");
            String ts = msg.get("timestamp");

            if (!roomLastSeen.containsKey(rId) || ts.compareTo(roomLastSeen.get(rId)) > 0) {
                roomLastSeen.put(rId, ts);
            }
        }

        List<Map<String, String>> result = new ArrayList<>();
        roomLastSeen.forEach((rId, ts) -> result.add(Map.of("roomId", rId, "lastActivity", ts)));
        return result;
    }

    // === Core Query 3 & Analytics (Optimized < 500ms) ===
    @Async("statsPool")
    @Cacheable(value = "analyticsCache", key = "{#start, #end}")
    public CompletableFuture<Map<String, Object>> getAnalyticsInWindow(String start, String end) {
        // SCATTER: Query 5 shards in parallel
        List<CompletableFuture<List<Map<String, AttributeValue>>>> futures = new ArrayList<>();
        for (int i = 0; i < NUM_SHARDS; i++) {
            futures.add(queryShardAsync(String.valueOf(i), start, end));
        }

        // GATHER: Combine results
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<Map<String, AttributeValue>> allItems = futures.stream()
                            .map(CompletableFuture::join)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    return calculateStats(allItems, start, end);
                });
    }

    private CompletableFuture<List<Map<String, AttributeValue>>> queryShardAsync(String bucketId, String start, String end) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, AttributeValue> eav = new HashMap<>();
            eav.put(":pk", AttributeValue.builder().s(bucketId).build());
            eav.put(":start", AttributeValue.builder().s(start).build());
            eav.put(":end", AttributeValue.builder().s(end).build());

            QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName(GSI_TIME)
                    .limit(50)
                    .keyConditionExpression("bucketId = :pk AND timestampSk BETWEEN :start AND :end")
                    .expressionAttributeValues(eav)
                    .build();
            return dynamoDbClient.query(request).items();
        });
    }

    private Map<String, Object> calculateStats(List<Map<String, AttributeValue>> items, String start, String end) {
        Set<String> uniqueUsers = new HashSet<>();
        Map<String, Integer> roomCounts = new HashMap<>();
        Map<String, Integer> userCounts = new HashMap<>();
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;

        for (Map<String, AttributeValue> item : items) {
            String uId = item.get("userId").s();
            String rId = item.get("roomId").s();
            uniqueUsers.add(uId);
            roomCounts.merge(rId, 1, Integer::sum);
            userCounts.merge(uId, 1, Integer::sum);
            try {
                String tsStr = item.get("timestampSk").s().split("#")[0];
                long ts = Instant.parse(tsStr).toEpochMilli();
                if(ts < minTime) minTime = ts;
                if(ts > maxTime) maxTime = ts;
            } catch (Exception e) {}
        }

        double duration = (maxTime > minTime) ? (maxTime - minTime) / 1000.0 : 1.0;
        double throughput = items.size() / (duration > 0 ? duration : 1);

        return Map.of(
                "window_start", start,
                "window_end", end,
                "unique_active_users", uniqueUsers.size(),
                "total_messages_in_window", items.size(),
                "throughput_msg_per_sec", String.format("%.2f", throughput),
                "top_active_users", userCounts.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(5).collect(Collectors.toList()),
                "top_active_rooms", roomCounts.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(5).collect(Collectors.toList())
        );
    }

    private List<Map<String, String>> executeQuery(QueryRequest request) {
        QueryResponse response = dynamoDbClient.query(request);
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            Map<String, String> map = new HashMap<>();
            item.forEach((k, v) -> map.put(k, v.s()));
            result.add(map);
        }
        return result;
    }
}