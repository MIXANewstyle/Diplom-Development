package com.diplom.contentservice.consumer;

import com.diplom.contentservice.config.RabbitMQConfig;
import com.diplom.contentservice.service.FollowsCacheService;
import com.diplom.contentservice.service.ModerationBlocklistService;
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
public class UserEventsConsumer {

    private final FollowsCacheService followsCacheService;
    private final ModerationBlocklistService moderationBlocklistService;
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
                case "user.account-moderated" -> {
                    handleAccountModerated(json);
                }
                default -> {
                    log.warn("Unhandled routing key on user-events queue: {}", routingKey);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to handle event routingKey={} payload={}",
                routingKey, json, ex);
            // Swallow — see Phase 6.2 rationale.
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

    private void handleAccountModerated(String json) throws JsonProcessingException {
        // Payload from user-service AccountModeratedEvent:
        //   { "userId": "...", "statusId": 1|2|3, "occurredAt": "..." }
        JsonNode node = objectMapper.readTree(json);

        UUID userId = UUID.fromString(node.get("userId").asText());
        int statusId = node.get("statusId").asInt();

        // user_statuses dictionary: 1=ACTIVE, 2=BANNED, 3=DELETED
        switch (statusId) {
            case 2, 3 -> moderationBlocklistService.addToBlocklist(userId);
            case 1    -> moderationBlocklistService.removeFromBlocklist(userId);
            default   -> log.warn("Unknown status_id {} in ACCOUNT_MODERATED for {}",
                            statusId, userId);
        }
    }
}
