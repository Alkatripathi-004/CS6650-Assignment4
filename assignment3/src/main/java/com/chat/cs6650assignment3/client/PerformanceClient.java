package com.chat.cs6650assignment3.client;

import com.chat.cs6650assignment3.messagegenerator.MessageGenerator;
import com.chat.cs6650assignment3.model.ChatMessage;
import com.google.common.util.concurrent.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class PerformanceClient {

    private static final String SERVER_WS_URL = "ws://chatApp-alb-841123983.us-east-1.elb.amazonaws.com/chat";
    private static final String SERVER_HTTP_URL = "http://chatApp-alb-841123983.us-east-1.elb.amazonaws.com";

    private static int NUM_THREADS = 64;
    private static double RATE_LIMIT_PER_SECOND = 500;
    private static int TOTAL_MESSAGES = 5000;

    public static final ChatMessage POISON_PILL = new ChatMessage();
    private static final int MESSAGE_QUEUE_CAPACITY = 30000;

    public static void main(String[] args) {
        System.out.println("\n=========== STARTING PERFORMANCE TEST (ASSIGNMENT 3) ===========");
        System.out.printf("Configuration: WS_URL=%s, HTTP_URL=%s%n", SERVER_WS_URL, SERVER_HTTP_URL);
        System.out.printf("Threads=%d, Messages=%d, Rate Limit=%.2f/s%n", NUM_THREADS, TOTAL_MESSAGES, RATE_LIMIT_PER_SECOND);

        runTestPhase();

        runAnalyticsVerification();

        System.out.println("=========== PERFORMANCE TEST COMPLETE ===========");
    }

    private static void runTestPhase() {
        System.out.println("\n--- PHASE 1: LOAD TESTING ---");
        String csvFilePath = String.format("results/performance_metrics_%d_threads_%d_msgs.csv", NUM_THREADS, TOTAL_MESSAGES);
        BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>(MESSAGE_QUEUE_CAPACITY);
        PerformanceReporter reporter = new PerformanceReporter(csvFilePath);
        RateLimiter sharedRateLimiter = RateLimiter.create(RATE_LIMIT_PER_SECOND);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS + 1);

        MessageGenerator generator = new MessageGenerator(messageQueue, TOTAL_MESSAGES, NUM_THREADS, POISON_PILL);
        List<WebSocketSenderTask> senderTasks = new ArrayList<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            senderTasks.add(new WebSocketSenderTask(SERVER_WS_URL, i, messageQueue, reporter, POISON_PILL, sharedRateLimiter));
        }

        long startTime = System.currentTimeMillis();

        executor.submit(generator);
        senderTasks.forEach(executor::submit);

        executor.shutdown();
        try {
            if (!executor.awaitTermination(45, TimeUnit.MINUTES)) {
                System.err.println("Executor did not terminate in the allotted time!");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("All tasks have completed execution.");

        int totalSuccess = senderTasks.stream().mapToInt(WebSocketSenderTask::getSuccessfulCount).sum();
        int totalFailed = senderTasks.stream().mapToInt(WebSocketSenderTask::getFailedCount).sum();
        int totalInitialConnections = senderTasks.stream().mapToInt(WebSocketSenderTask::getTotalConnections).sum();
        int totalReconnections = senderTasks.stream().mapToInt(WebSocketSenderTask::getTotalReconnections).sum();
        List<Long> allLatencies = senderTasks.stream()
                .flatMap(task -> task.getLatencies().stream())
                .collect(Collectors.toList());
        double durationSeconds = (endTime - startTime) / 1000.0;
        double throughput = (durationSeconds > 0) ? totalSuccess / durationSeconds : 0;

        System.out.println("\n--- Load Test Results ---");
        System.out.println("Total Successful Messages: " + totalSuccess);
        System.out.println("Total Failed Messages: " + totalFailed);
        System.out.println("Total Initial Connections: " + totalInitialConnections);
        System.out.println("Total Reconnections: " + totalReconnections);
        System.out.printf("Total Runtime: %.2f seconds%n", durationSeconds);
        System.out.printf("Throughput: %.2f messages/second%n", throughput);
        reporter.printStatistics(allLatencies);
        reporter.writeToCsv();

    }

    private static void runAnalyticsVerification() {
        System.out.println("\n--- PHASE 2: ANALYTICS & PERSISTENCE VERIFICATION ---");
        System.out.println("Waiting 30 seconds to allow Write-Behind buffer to flush to DynamoDB...");
        try { Thread.sleep(10000); } catch (InterruptedException e) {}

        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        try {
            System.out.println("\n[Query 1] Fetching System Analytics Stats...");
            String statsUrl = SERVER_HTTP_URL + "/api/analytics/stats";
            String statsJson = sendGetRequest(httpClient, statsUrl);

            JsonNode statsNode = mapper.readTree(statsJson);

            System.out.println("Response:\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(statsNode));

            String now = Instant.now().toString();
            String oneHourAgo = Instant.now().minusSeconds(3600).toString();
            System.out.println("\n[Query 2] Fetching History for Room 1...");
            String roomUrl = String.format("%s/api/analytics/room/1?start=%s&end=%s", SERVER_HTTP_URL, oneHourAgo, now);
            String roomJson = sendGetRequest(httpClient, roomUrl);
            JsonNode roomNode = mapper.readTree(roomJson);
            System.out.println(">> Messages found in Room 1: " + roomNode.size());

            System.out.println("\n[Query 3] Fetching History for User ID '1'...");
            String userUrl = SERVER_HTTP_URL + "/api/analytics/user/1";
            String userJson = sendGetRequest(httpClient, userUrl);
            JsonNode userNode = mapper.readTree(userJson);

            if (userNode.has("history")) {
                System.out.println(">> Messages found for User 1: " + userNode.path("history").size());
            } else {
                System.out.println(">> Messages found for User 1: " + userNode.size());
            }

            System.out.println("\n[SUCCESS] Analytics API verification completed.");

        } catch (Exception e) {
            System.err.println("\n[FAILURE] Analytics verification failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String sendGetRequest(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP Request failed with status " + response.statusCode());
        }
        return response.body();
    }
}