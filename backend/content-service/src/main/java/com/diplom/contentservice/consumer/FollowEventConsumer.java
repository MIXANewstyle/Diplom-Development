package com.diplom.contentservice.consumer;

import com.diplom.contentservice.config.RabbitMQConfig;
import com.diplom.contentservice.service.FollowsCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FollowEventConsumer {

    private final FollowsCacheService followsCacheService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.USER_EVENTS_QUEUE)
    public void onUserEvent(
        @Payload String json,
        @Header("amqp_receivedRoutingKey") String routingKey
    ) {
        try {
            switch (routingKey) {
                case "user.follow-added", "user.follow-removed" -> {
                    UUID followerId = extractFollowerId(json);
                    followsCacheService.invalidate(followerId);
                }
                default -> log.warn("Unhandled routing key on user-events queue: {}", routingKey);
            }
        } catch (Exception ex) {
            // Swallow — a single bad message must not nack/requeue forever.
            // The 5-min TTL on the cache acts as a safety net for missed invalidations.
            log.error("Failed to handle event routingKey={} payload={}",
                routingKey, json, ex);
        }
    }

    private UUID extractFollowerId(String json) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(json);
        JsonNode field = node.get("followerId");
        if (field == null || field.isNull()) {
            throw new IllegalArgumentException("Payload missing followerId");
        }
        return UUID.fromString(field.asText());
    }
}
