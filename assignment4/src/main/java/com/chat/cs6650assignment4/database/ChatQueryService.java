package com.chat.cs6650assignment4.database;

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

    // === CHANGED: Added Caching ===
    // Cache Key includes roomId + start + end to ensure uniqueness per query range
    @Cacheable(value = "roomHistory", key = "{#roomId, #start, #end}")
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
                .limit(100) // Added limit for safety/performance
                .build();
        return executeQuery(request);
    }

    // === CHANGED: Added Caching ===
    @Cacheable(value = "userHistory", key = "{#userId, #start, #end}")
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
                .limit(100) // Added limit
                .build();
        return executeQuery(request);
    }

    // === CHANGED: Added Caching ===
    @Cacheable(value = "userRooms", key = "#userId")
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

    @Cacheable(value = "analyticsCache", key = "{#start, #end}")
    public Map<String, Object> getAnalyticsInWindow(String start, String end) {
        // SCATTER: Query 5 shards in parallel
        List<CompletableFuture<List<Map<String, AttributeValue>>>> futures = new ArrayList<>();
        for (int i = 0; i < NUM_SHARDS; i++) {
            futures.add(queryShardAsync(String.valueOf(i), start, end));
        }

        // GATHER: Wait for all to finish (join)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // MERGE results
        List<Map<String, AttributeValue>> allItems = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // CALCULATE stats
        return calculateStats(allItems, start, end);
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
                    .keyConditionExpression("bucketId = :pk AND timestampSk BETWEEN :start AND :end")
                    .expressionAttributeValues(eav)
                    .limit(50)
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

        List<Map<String, Integer>> topUsers = userCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    // Create a fresh HashMap to ensure Serializability
                    HashMap<String, Integer> map = new HashMap<>();
                    map.put(e.getKey(), e.getValue());
                    return map;
                })
                .collect(Collectors.toList()); // Use standard list

        // 2. Convert Top Rooms to simple List of Maps
        List<Map<String, Integer>> topRooms = roomCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    HashMap<String, Integer> map = new HashMap<>();
                    map.put(e.getKey(), e.getValue());
                    return map;
                })
                .collect(Collectors.toList());

        // 3. Return a clean HashMap (Not Map.of() which is immutable/internal)
        Map<String, Object> result = new HashMap<>();
        result.put("window_start", start);
        result.put("window_end", end);
        result.put("unique_active_users", uniqueUsers.size());
        result.put("total_messages_in_window", items.size());
        result.put("throughput_msg_per_sec", String.format("%.2f", throughput));
        result.put("top_active_users", new ArrayList<>(topUsers)); // Wrap in ArrayList
        result.put("top_active_rooms", new ArrayList<>(topRooms)); // Wrap in ArrayList

        return result;
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