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

        String endTime = (end != null) ? end : Instant.now().toString();
        String startTime = (start != null) ? start : Instant.now().minus(1, ChronoUnit.HOURS).toString();

        return queryService.getRoomHistory(roomId, startTime, endTime);
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
    public CompletableFuture<Map<String, Object>> getActiveUserCount(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        String endTime = (end != null) ? end : Instant.now().toString();
        String startTime = (start != null) ? start : Instant.now().minus(1, ChronoUnit.HOURS).toString();

        return queryService.getAnalyticsInWindow(startTime, endTime)
                .thenApply(stats -> Map.of(
                        "window_start", stats.get("window_start"),
                        "window_end", stats.get("window_end"),
                        "unique_active_users", stats.get("unique_active_users")
                ));
    }


    @GetMapping("/user/{userId}/rooms")
    public List<Map<String, String>> getUserRooms(@PathVariable String userId) {
        return queryService.getRoomsForUser(userId);
    }

    @GetMapping("/stats")
    public CompletableFuture<Map<String, Object>> getSystemStats(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        String endTime = (end != null) ? end : Instant.now().toString();
        String startTime = (start != null) ? start : Instant.now().minus(1, ChronoUnit.HOURS).toString();

        return queryService.getAnalyticsInWindow(startTime, endTime);
    }
}