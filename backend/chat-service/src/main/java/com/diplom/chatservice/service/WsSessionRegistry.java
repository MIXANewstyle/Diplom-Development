package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.ws.SessionTerminatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WsSessionRegistry {

    private final SimpMessagingTemplate messagingTemplate;

    // TODO: move to Redis + a "terminate userId" pub/sub for multi-instance in Phase 4f
    private final Map<UUID, Set<String>> userIdToSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessionIdToSession = new ConcurrentHashMap<>();

    public void registerUserId(UUID userId, String sessionId) {
        userIdToSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void unregisterSessionId(String sessionId) {
        userIdToSessions.values().forEach(sessions -> sessions.remove(sessionId));
        sessionIdToSession.remove(sessionId);
    }

    public void registerWebSocketSession(String sessionId, WebSocketSession session) {
        sessionIdToSession.put(sessionId, session);
    }

    public void unregisterWebSocketSession(String sessionId) {
        sessionIdToSession.remove(sessionId);
    }

    public void terminateUserSessions(UUID userId) {
        Set<String> sessions = userIdToSessions.remove(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        for (String sessionId : sessions) {
            WebSocketSession session = sessionIdToSession.remove(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    if (session.getPrincipal() != null) {
                        messagingTemplate.convertAndSendToUser(
                            session.getPrincipal().getName(),
                            "/queue/errors",
                            new SessionTerminatedEvent("MODERATION")
                        );
                    }
                    session.close(new CloseStatus(4000, "moderated"));
                    log.info("Terminated WebSocketSession {} for user {}", sessionId, userId);
                } catch (Exception e) {
                    log.error("Failed to close session {} for user {}", sessionId, userId, e);
                }
            }
        }
    }
}
