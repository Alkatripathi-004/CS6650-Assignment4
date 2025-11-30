package com.chat.cs6650assignment4.model;

public class ServerResponse {
    private String status;
    private String serverTimestamp;
    private Object message;
    private String originalMessageId;


    public ServerResponse(String status, String serverTimestamp) {
        this.status = status;
        this.serverTimestamp = serverTimestamp;
    }

    public ServerResponse(String status, String serverTimestamp, Object message) {
        this.status = status;
        this.serverTimestamp = serverTimestamp;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }


    public void setMessage(Object message) {
        this.message = message;
    }

    public void setData(Object errorMessage) {
        this.message = errorMessage;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }
    public void setOriginalMessageId(String originalMessageId) {
        this.originalMessageId = originalMessageId;
    }
}
