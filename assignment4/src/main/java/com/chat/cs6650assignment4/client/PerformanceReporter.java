package com.chat.cs6650assignment4.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PerformanceReporter {

    private final ConcurrentLinkedQueue<PerformanceEntry> entries = new ConcurrentLinkedQueue<PerformanceEntry>();
    private final String csvFilePath;

    public PerformanceReporter(String csvFilePath) {
        this.csvFilePath = csvFilePath;
        try {
            File csvFile = new File(csvFilePath);
            File parentDir = csvFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) throw new IOException("Failed to create dir");
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, false))) {
                writer.println("No.,StartTimestamp,EndTimestamp,MessageType,Latency,StatusCode,RoomId");
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize CSV file: " + e.getMessage());
        }
    }

    public void addEntry(PerformanceEntry entry) {
        entries.add(entry);
    }

    public void writeToCsv() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFilePath, true))) {
            int idx = 0;
            PerformanceEntry entry;
            while ((entry = entries.poll()) != null) {
                writer.println(entry.toCsvRow(idx++));
            }
        } catch (IOException e) {
            System.err.println("Error writing performance data to CSV file: " + e.getMessage());
        }
    }

    public void printStatistics(List<Long> latencies) {
        if (latencies.isEmpty()) {
            System.out.println("No successful requests to report statistics on.");
            return;
        }

        Collections.sort(latencies);

        int size = latencies.size();
        long sum = latencies.stream().mapToLong(Long::longValue).sum();
        double mean = (double) sum / size;

        long min = latencies.get(0);
        long max = latencies.get(size - 1);
        long p50 = latencies.get((int) (size * 0.50));
        long p95 = latencies.get((int) (size * 0.95));
        long p99 = latencies.get((int) (size * 0.99));

        System.out.println("\n------------------------------------------------");
        System.out.println("           CLIENT SIDE LATENCY METRICS          ");
        System.out.println("------------------------------------------------");
        System.out.printf("Mean Response Time:   %.2f ms%n", mean);
        System.out.printf("Min Response Time:    %d ms%n", min);
        System.out.printf("Max Response Time:    %d ms%n", max);
        System.out.println("------------------------------------------------");
        System.out.printf("P50 (Median) Latency: %d ms%n", p50);
        System.out.printf("P95 Latency:          %d ms%n", p95);
        System.out.printf("P99 Latency:          %d ms%n", p99);
        System.out.println("------------------------------------------------");
    }
}