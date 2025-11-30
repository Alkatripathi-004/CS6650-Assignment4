package com.chat.cs6650assignment4.model;

import java.time.Instant;
import java.util.UUID;

public class ChatMessage {
    private String messageId;
    private String userId;
    private String username;
    private String message;
    private String timestamp;
    private MessageType messageType;

    public enum MessageType {
        TEXT, JOIN, LEAVE
    }

    public ChatMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = Instant.now().toString();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "messageId='" + messageId + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", message='" + message + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", messageType=" + messageType +
                '}';
    }
}
