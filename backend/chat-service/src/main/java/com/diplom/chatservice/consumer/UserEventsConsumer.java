package com.diplom.chatservice.consumer;

import com.diplom.chatservice.config.RabbitMQConfig;
import com.diplom.chatservice.service.FriendLinkProjectionService;
import com.diplom.chatservice.service.ModerationBlocklistService;
import com.diplom.chatservice.service.RoleCacheService;
import com.diplom.chatservice.service.RoomService;
import com.diplom.chatservice.consumer.dto.RoleUpdatedEvent;
import com.diplom.chatservice.consumer.dto.AccountModeratedEvent;
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
    private final ModerationBlocklistService moderationBlocklistService;
    private final RoleCacheService roleCacheService;
    private final RoomService roomService;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfig.USER_EVENTS_QUEUE)
    public void onUserEvent(
        @Payload String json,
        @Header("amqp_receivedRoutingKey") String routingKey
    ) {
        try {
            switch (routingKey) {
                case "user.friendship-accepted" -> handleFriendshipAccepted(json);
                case "user.role-updated" -> handleRoleUpdated(json);
                case "user.account-moderated" -> handleAccountModerated(json);
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

    private void handleRoleUpdated(String json) throws JsonProcessingException {
        RoleUpdatedEvent event = objectMapper.readValue(json, RoleUpdatedEvent.class);
        String roleStr = roleName(event.roleId());
        if (roleStr == null) {
            // Never downgrade on an unknown id: the cache gates room creation, so writing a guessed
            // low role here would lock the user out until the next ROLE_UPDATED. Keep the old value.
            log.warn("Unknown roleId {} in ROLE_UPDATED for user {} — role cache left unchanged",
                    event.roleId(), event.userId());
            return;
        }
        roleCacheService.putRole(event.userId(), roleStr);
    }

    /** user_schema.user_roles (V2 seeds 1-4, V4 adds 5). Returns null for ids this service doesn't know. */
    private static String roleName(Integer roleId) {
        if (roleId == null) return null;
        return switch (roleId) {
            case 1 -> "GUEST";
            case 2 -> "FREE";
            case 3 -> "BASIC";
            case 4 -> "AUTHOR";
            case 5 -> "ADMIN";
            default -> null;
        };
    }

    private void handleAccountModerated(String json) throws JsonProcessingException {
        AccountModeratedEvent event = objectMapper.readValue(json, AccountModeratedEvent.class);
        switch (event.statusId()) {
            case 2, 3 -> {
                moderationBlocklistService.block(event.userId());
                roomService.abandonRoomsForBannedUser(event.userId());
                stringRedisTemplate.convertAndSend("chat:control:terminate-user", event.userId().toString());
            }
            case 1 -> moderationBlocklistService.unblock(event.userId());
            default -> log.warn("Unknown statusId {} in ACCOUNT_MODERATED for {}", event.statusId(), event.userId());
        }
    }
}
