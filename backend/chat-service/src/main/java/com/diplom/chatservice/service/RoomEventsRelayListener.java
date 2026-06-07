package com.diplom.chatservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomEventsRelayListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String jsonPayload = new String(message.getBody(), StandardCharsets.UTF_8);

            // Channel is "room.events.{roomId}"
            String prefix = "room.events.";
            if (channel.startsWith(prefix)) {
                String roomIdStr = channel.substring(prefix.length());

                // Parse json to JsonNode to ensure consistent formatting 
                // and preserve all fields regardless of origin instance.
                JsonNode parsedPayload = objectMapper.readTree(jsonPayload);

                String dest = "/topic/rooms/" + roomIdStr;
                messagingTemplate.convertAndSend(dest, parsedPayload);
            }
        } catch (Exception e) {
            log.error("Failed to relay room event: {}", e.getMessage(), e);
        }
    }
}
