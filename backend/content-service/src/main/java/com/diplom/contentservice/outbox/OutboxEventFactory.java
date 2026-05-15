package com.diplom.contentservice.outbox;

import com.diplom.contentservice.entity.ContentOutboxEvent;
import com.diplom.contentservice.event.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventFactory {
    private final ObjectMapper objectMapper;

    public ContentOutboxEvent create(EventType type, Object payloadDto) {
        try {
            String json = objectMapper.writeValueAsString(payloadDto);
            return ContentOutboxEvent.builder()
                .eventType(type.name())
                .payload(json)
                .status("PENDING")
                .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload for " + type, e);
        }
    }
}
