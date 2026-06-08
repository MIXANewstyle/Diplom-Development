package com.diplom.billingservice.outbox;

import com.diplom.billingservice.entity.BillingOutboxEvent;
import com.diplom.billingservice.event.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public BillingOutboxEvent create(EventType type, Object payloadDto) {
        try {
            String payload = objectMapper.writeValueAsString(payloadDto);
            return BillingOutboxEvent.builder()
                    .eventType(type.name())
                    .payload(payload)
                    .status("PENDING")
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}
