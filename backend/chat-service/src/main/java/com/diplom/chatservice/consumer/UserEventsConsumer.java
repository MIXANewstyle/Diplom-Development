package com.diplom.chatservice.consumer;

import com.diplom.chatservice.config.RabbitMQConfig;
import com.diplom.chatservice.service.FriendLinkProjectionService;
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

    private final FriendLinkProjectionService friendLinkProjectionService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.USER_EVENTS_QUEUE)
    public void onUserEvent(
        @Payload String json,
        @Header("amqp_receivedRoutingKey") String routingKey
    ) {
        try {
            switch (routingKey) {
                case "user.friendship-accepted" -> handleFriendshipAccepted(json);
                default -> log.warn("Unhandled routing key on user-events queue: {}", routingKey);
            }
        } catch (Exception ex) {
            log.error("Failed to handle event routingKey={} payload={}", routingKey, json, ex);
        }
    }

    private void handleFriendshipAccepted(String json) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(json);
        UUID requesterId = UUID.fromString(node.get("requesterId").asText());
        UUID addresseeId = UUID.fromString(node.get("addresseeId").asText());
        friendLinkProjectionService.upsertFriendship(requesterId, addresseeId);
    }
}
