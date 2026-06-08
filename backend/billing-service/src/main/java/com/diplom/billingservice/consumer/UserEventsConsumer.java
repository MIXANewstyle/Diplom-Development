package com.diplom.billingservice.consumer;

import com.diplom.billingservice.config.RabbitMQConfig;
import com.diplom.billingservice.service.BillingAccountService;
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

    private final BillingAccountService billingAccountService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.USER_EVENTS_QUEUE)
    public void onUserEvent(
            @Payload String json,
            @Header("amqp_receivedRoutingKey") String routingKey
    ) {
        try {
            switch (routingKey) {
                case "user.registered" -> {
                    JsonNode node = objectMapper.readTree(json);
                    UUID userId = UUID.fromString(node.get("userId").asText());
                    billingAccountService.createIfAbsent(userId);
                }
                default -> {
                    log.warn("Unhandled routing key on user-events queue: {}", routingKey);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to handle event routingKey={} payload={}", routingKey, json, ex);
        }
    }
}
