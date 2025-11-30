package com.chat.cs6650assignment4.client;

import com.chat.cs6650assignment4.model.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WebSocketSenderTask implements Runnable {

    private final BlockingQueue<ChatMessage> messageQueue;
    private final PerformanceReporter reporter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, PendingRequest> pendingMessages;
    private WebSocketClient client;
    private final ChatMessage poisonPill;
    private final int workerId;
    private final int roomId;
    private final URI serverUri;
    private final RateLimiter rateLimiter;

    private final AtomicInteger successfulMessages;
    private final AtomicInteger failedMessages;
    private final List<Long> latencies;
    private final AtomicInteger totalConnections;
    private final AtomicInteger totalReconnections;
    private final AtomicLong lastMessageReceivedTime;

    public WebSocketSenderTask(String serverBaseUrl, int workerId, BlockingQueue<ChatMessage> messageQueue,
                               PerformanceReporter reporter, ChatMessage poisonPill, RateLimiter rateLimiter) {
        this.workerId = workerId;
        this.messageQueue = messageQueue;
        this.reporter = reporter;
        this.poisonPill = poisonPill;
        this.roomId = workerId % 20 + 1;
        this.serverUri = URI.create(serverBaseUrl + "/" + this.roomId);
        this.pendingMessages = new ConcurrentHashMap<>();
        this.successfulMessages = new AtomicInteger(0);
        this.failedMessages = new AtomicInteger(0);
        this.totalConnections = new AtomicInteger(0);
        this.totalReconnections = new AtomicInteger(0);
        this.latencies = Collections.synchronizedList(new ArrayList<>());
        this.rateLimiter = rateLimiter;
        this.lastMessageReceivedTime = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public void run() {
        try {
            ensureConnection();
            while (true) {
                ChatMessage message = messageQueue.take();
                if (message == poisonPill) {
                    break;
                }
                rateLimiter.acquire();
                try {
                    ensureConnection();
                    message.setTimestamp(Instant.now().toString());
                    sendMessage(message);
                } catch (Exception e) {
                    failMessage(message, "Unhandled exception in send loop: " + e.getMessage());
                }
            }
            waitForCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeConnection();
        }
    }

    private void ensureConnection() throws InterruptedException {
        if (client == null || !client.isOpen()) {
            if (client != null) { totalReconnections.incrementAndGet(); } else { totalConnections.incrementAndGet(); }
            client = createClient(serverUri);
            if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Connection to " + serverUri + " failed");
            }
        }
    }

    private void sendMessage(ChatMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            pendingMessages.put(message.getMessageId(), new PendingRequest(message));
            client.send(jsonMessage);
        } catch (Exception e) {
            failMessage(message, "Exception during send: " + e.getMessage());
        }
    }

    private void failMessage(ChatMessage message, String reason) {
        if (message != null && pendingMessages.remove(message.getMessageId()) != null) {
            failedMessages.incrementAndGet();
        }
    }

    private void waitForCompletion() {
        final long INACTIVITY_TIMEOUT_MS = 120000;
        while (!pendingMessages.isEmpty()) {
            long timeSinceLastMessage = System.currentTimeMillis() - lastMessageReceivedTime.get();
            if (timeSinceLastMessage > INACTIVITY_TIMEOUT_MS) {
                System.err.println("[" + Thread.currentThread().getName() + "] TIMED OUT due to inactivity. "
                        + pendingMessages.size() + " messages did not complete.");
                break;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!pendingMessages.isEmpty()) {
            pendingMessages.values().forEach(pr -> failMessage(pr.message, "Inactivity Timeout"));
            pendingMessages.clear();
        }
    }

    private WebSocketClient createClient(URI serverUri) {
        return new WebSocketClient(serverUri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("[" + Thread.currentThread().getName() + "] Connected to " + serverUri);
            }

            @Override
            public void onMessage(String message) {
                lastMessageReceivedTime.set(System.currentTimeMillis());
                try {
                    JsonNode responseNode = objectMapper.readTree(message);
                    if (responseNode.has("clientMessageId")) {
                        handleBroadcast(responseNode);
                    }
                } catch (Exception e) {
                }
            }

            private void handleBroadcast(JsonNode broadcastNode) {
                String originalId = broadcastNode.get("clientMessageId").asText();
                PendingRequest pending = pendingMessages.remove(originalId);
                if (pending != null) {
                    successfulMessages.incrementAndGet();
                    long latency = System.currentTimeMillis() - pending.startTime;
                    latencies.add(latency);
                    reporter.addEntry(new PerformanceEntry(
                            pending.message.getTimestamp(),
                            Instant.now().toString(),
                            pending.message.getMessageType().toString(),
                            latency,
                            200,
                            roomId
                    ));
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {}
            @Override
            public void onError(Exception ex) {}
        };
    }

    private void closeConnection() {
        if (client != null && client.isOpen()) {
            client.close();
        }
    }

    public int getSuccessfulCount() { return successfulMessages.get(); }
    public int getFailedCount() { return failedMessages.get(); }
    public List<Long> getLatencies() { return latencies; }
    public int getTotalConnections() { return totalConnections.get(); }
    public int getTotalReconnections() { return totalReconnections.get(); }

    class PendingRequest {
        final ChatMessage message;
        final long startTime;

        public PendingRequest(ChatMessage message) {
            this.message = message;
            this.startTime = System.currentTimeMillis();
        }
    }
}