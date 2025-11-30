package com.chat.cs6650assignment4.serverv4;

import com.chat.cs6650assignment4.model.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.util.Set;

@Service
public class BroadcastConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastConsumerService.class);
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public BroadcastConsumerService(SessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{serverBroadcastQueue.name}")
    public void receiveBroadcast(QueueMessage message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(messageJson);

            Set<WebSocketSession> sessionsInRoom = sessionManager.getSessions(message.getRoomId());
            for (WebSocketSession session : sessionsInRoom) {
                try {
                    if (session.isOpen()) {
                        synchronized(session) { session.sendMessage(textMessage); }
                    }
                } catch (Exception e) {
                    logger.error("Failed to send broadcast to session {}", session.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Critical error processing broadcast message", e);
        }
    }
}