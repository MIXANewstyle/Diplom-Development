package com.diplom.userservice.outbox;

import com.diplom.userservice.entity.UserOutboxEvent;
import com.diplom.userservice.event.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public UserOutboxEvent create(EventType type, Object payloadDto) {
        try {
            String payload = objectMapper.writeValueAsString(payloadDto);
            return UserOutboxEvent.builder()
                    .eventType(type.name())
                    .payload(payload)
                    .status("PENDING")
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}
