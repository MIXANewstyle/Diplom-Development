package com.diplom.userservice.consumer;

import com.diplom.userservice.config.RabbitMQConfig;
import com.diplom.userservice.repository.UserRepository;
import com.diplom.userservice.service.UserService;
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
public class BillingEventsConsumer {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final int ROLE_FREE_ID  = 2;  // user_roles: FREE
    private static final int ROLE_BASIC_ID = 3;  // user_roles: BASIC

    @RabbitListener(queues = RabbitMQConfig.BILLING_EVENTS_QUEUE)
    public void onBillingEvent(
            @Payload String json,
            @Header("amqp_receivedRoutingKey") String routingKey) {
        try {
            if ("billing.subscription-changed".equals(routingKey)) {
                handleSubscriptionChanged(json);
            } else {
                log.warn("Unhandled routing key on billing-events queue: {}", routingKey);
            }
        } catch (Exception ex) {
            log.error("Failed to handle billing event routingKey={} payload={}", routingKey, json, ex);
            // swallow — mirrors content-service rationale (avoid poison-message requeue loops)
        }
    }

    private void handleSubscriptionChanged(String json) throws JsonProcessingException {
        // Payload (§7.1): { userId, newTier: "BASIC"|"FREE", expiresAt, occurredAt }
        JsonNode node = objectMapper.readTree(json);
        UUID userId = UUID.fromString(node.get("userId").asText());
        String newTier = node.get("newTier").asText();

        Integer targetRoleId = switch (newTier) {
            case "BASIC" -> ROLE_BASIC_ID;
            case "FREE"  -> ROLE_FREE_ID;
            default -> null;
        };
        if (targetRoleId == null) {
            log.warn("Unknown newTier '{}' in SUBSCRIPTION_CHANGED for user {}", newTier, userId);
            return;
        }

        // Idempotency / no-op guard: only update when the role actually changes.
        // Billing emits SUBSCRIPTION_CHANGED{BASIC} even on pure renewals (§3.2); without this guard
        // each renewal would emit a redundant ROLE_UPDATED.
        userRepository.findById(userId).ifPresentOrElse(user -> {
            if (!java.util.Objects.equals(user.getRoleId(), targetRoleId)) {
                userService.updateUserRole(userId, targetRoleId);   // persists role + emits ROLE_UPDATED (one tx)
                log.info("SUBSCRIPTION_CHANGED applied: user {} role -> {} ({})", userId, targetRoleId, newTier);
            } else {
                log.debug("SUBSCRIPTION_CHANGED no-op: user {} already at role {}", userId, targetRoleId);
            }
        }, () -> log.warn("SUBSCRIPTION_CHANGED for unknown user {}", userId));
    }
}
