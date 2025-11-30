package com.chat.cs6650assignment4.serverv4;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionManager {
    private final ConcurrentHashMap<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public void addSession(String roomId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void removeSession(String roomId, WebSocketSession session) {
        roomSessions.computeIfPresent(roomId, (k, v) -> {
            v.remove(session);
            return v.isEmpty() ? null : v;
        });
    }

    public Set<WebSocketSession> getSessions(String roomId) {
        return roomSessions.getOrDefault(roomId, Collections.emptySet());
    }
}
