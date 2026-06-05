package com.diplom.chatservice.outbox;

import com.diplom.chatservice.entity.ChatOutboxEvent;
import com.diplom.chatservice.event.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventFactory {
    private final ObjectMapper objectMapper;

    public ChatOutboxEvent create(EventType type, Object payloadDto) {
        try {
            String json = objectMapper.writeValueAsString(payloadDto);
            return ChatOutboxEvent.builder()
                .eventType(type.name())
                .payload(json)
                .status("PENDING")
                .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload for " + type, e);
        }
    }
}
