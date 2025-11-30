package com.chat.cs6650assignment4.client;

public class PerformanceEntry {
    private final String startTimestamp;
    private final String endTimestamp;
    private final String messageType;
    private final long latency;
    private final int statusCode;
    private final int roomId;

    public PerformanceEntry(String startTimestamp, String endTimestamp, String messageType, long latency, int statusCode, int roomId) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.messageType = messageType;
        this.latency = latency;
        this.statusCode = statusCode;
        this.roomId = roomId;
    }

    public String toCsvRow(int index) {
        return String.format("%d,%s,%s,%s,%d,%d,%d",
                index, startTimestamp, endTimestamp, messageType, latency, statusCode, roomId);
    }
}