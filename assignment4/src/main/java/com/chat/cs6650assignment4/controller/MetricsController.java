package com.chat.cs6650assignment4.controller;

import com.chat.cs6650assignment4.database.ChatQueryService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/analytics")
public class MetricsController {

    private final ChatQueryService queryService;

    public MetricsController(ChatQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/room/{roomId}")
    public List<Map<String, String>> getRoomHistory(
            @PathVariable String roomId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        if (end == null) {
            // Truncate to minutes (e.g., 10:05:00 instead of 10:05:23.456)
            end = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString();
        }
        if (start == null) {
            start = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.HOURS).toString();
        }
        return queryService.getRoomHistory(roomId, start, end);
    }

    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserDetails(
            @PathVariable String userId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        return Map.of(
                "history", queryService.getUserHistory(userId, start, end),
                "participated_rooms", queryService.getRoomsForUser(userId)
        );
    }

    @GetMapping("/active-users")
    public Map<String, Object> getActiveUserCount(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        if (end == null) {
            // Truncate to minutes (e.g., 10:05:00 instead of 10:05:23.456)
            end = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString();
        }
        if (start == null) {
            start = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.HOURS).toString();
        }

        Map<String, Object> stats = queryService.getAnalyticsInWindow(start, end);

        return Map.of(
                "window_start", stats.get("window_start"),
                "window_end", stats.get("window_end"),
                "unique_active_users", stats.get("unique_active_users")
        );
    }


    @GetMapping("/user/{userId}/rooms")
    public List<Map<String, String>> getUserRooms(@PathVariable String userId) {
        return queryService.getRoomsForUser(userId);
    }

    @GetMapping("/stats")
    public Map<String, Object> getSystemStats(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        if (end == null) {
            // Truncate to minutes (e.g., 10:05:00 instead of 10:05:23.456)
            end = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString();
        }
        if (start == null) {
            start = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.HOURS).toString();
        }

        return queryService.getAnalyticsInWindow(start, end);
    }
}