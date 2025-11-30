package com.chat.cs6650assignment4.serverv4;

import com.chat.cs6650assignment4.model.ChatMessage;
import com.chat.cs6650assignment4.model.QueueMessage;
import com.chat.cs6650assignment4.model.ServerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Component
public class ChatWebSocketHandler  extends TextWebSocketHandler {
    private final RabbitMQProducerService producerService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final String serverId = "server-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    public static final String ROOM_ID_ATTRIBUTE = "roomId";

    public ChatWebSocketHandler(RabbitMQProducerService producerService, SessionManager sessionManager, ObjectMapper objectMapper) {
        this.producerService = producerService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            String roomId = getRoomId(session);
            if (roomId == null || roomId.isBlank()) {
                session.close(CloseStatus.BAD_DATA.withReason("Room ID is missing or invalid."));
                return;
            }

            sessionManager.addSession(roomId, session);

        } catch (Exception e) {
            System.err.println("Error during connection establishment: " + e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            ChatMessage chatMessage = objectMapper.readValue(message.getPayload(), ChatMessage.class);
            validateMessage(chatMessage);
            QueueMessage queueMessage = new QueueMessage();
            queueMessage.setMessageId(java.util.UUID.randomUUID().toString());
            queueMessage.setRoomId(getRoomId(session));
            queueMessage.setUserId(chatMessage.getUserId());
            queueMessage.setUsername(chatMessage.getUsername());
            queueMessage.setMessage(chatMessage.getMessage());
            queueMessage.setTimestamp(chatMessage.getTimestamp());
            queueMessage.setMessageType(chatMessage.getMessageType());
            queueMessage.setServerId(serverId);
            queueMessage.setClientIp(session.getRemoteAddress().toString());
            queueMessage.setClientMessageId(chatMessage.getMessageId());
            producerService.publishMessage(queueMessage);
            String originalId = chatMessage.getMessageId();
            ServerResponse response = new ServerResponse("OK", Instant.now().toString(), message);
            response.setOriginalMessageId(originalId);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            }

        } catch (Exception e) {
            System.out.println("Error while publishing message to queue: " + e.getMessage());
            ServerResponse errorResponse = new ServerResponse("ERROR", Instant.now().toString());
            errorResponse.setMessage(e.getMessage());
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
                    }
                }
            } catch (IOException ioException) {

            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = (String) session.getAttributes().get(ROOM_ID_ATTRIBUTE);

        if (roomId != null) {
            sessionManager.removeSession(roomId, session);
            System.out.println("Connection closed from " + session.getRemoteAddress() + " for room " + roomId + " with status " + status);
        } else {
            System.out.println("Connection closed from " + session.getRemoteAddress() + " (room ID unknown) with status " + status);
        }
    }

    private String getRoomId(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getPath() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == path.length() - 1) {
            return null;
        }
        return path.substring(lastSlash + 1);
    }

    private void validateMessage(ChatMessage msg) {
        if (msg.getUserId() == null) throw new IllegalArgumentException("userId is required.");
        try {
            int userIdInt = Integer.parseInt(msg.getUserId());
            if (userIdInt < 1 || userIdInt > 100000) {
                throw new IllegalArgumentException("userId must be between 1 and 100000.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("userId must be a valid integer string.");
        }

        if (msg.getUsername() == null || !USERNAME_PATTERN.matcher(msg.getUsername()).matches()) {
            throw new IllegalArgumentException("username must be 3-20 alphanumeric characters.");
        }

        if (msg.getMessage() == null || msg.getMessage().length() < 1 || msg.getMessage().length() > 500) {
            throw new IllegalArgumentException("message must be between 1 and 500 characters.");
        }

        if (msg.getTimestamp() == null) throw new IllegalArgumentException("timestamp is required.");
        try {
            Instant.parse(msg.getTimestamp());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("timestamp must be a valid ISO-8601 timestamp.");
        }

        if (msg.getMessageType() == null) {
            throw new IllegalArgumentException("messageType must be TEXT, JOIN, or LEAVE.");
        }
    }
}
