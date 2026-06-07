package com.diplom.chatservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionTerminationListener implements MessageListener {

    private final WsSessionRegistry wsSessionRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String userIdStr = new String(message.getBody(), StandardCharsets.UTF_8);
            
            // Remove wrapping quotes if Jackson added them during publish
            if (userIdStr.startsWith("\"") && userIdStr.endsWith("\"")) {
                userIdStr = userIdStr.substring(1, userIdStr.length() - 1);
            }
            
            UUID userId = UUID.fromString(userIdStr);
            log.info("Received session termination control message for user {}", userId);
            
            wsSessionRegistry.terminateUserSessions(userId);
        } catch (Exception e) {
            log.error("Failed to process session termination message: {}", e.getMessage(), e);
        }
    }
}
