package com.diplom.chatservice.consumer;

import com.diplom.chatservice.service.FriendLinkProjectionService;
import com.diplom.chatservice.service.ModerationBlocklistService;
import com.diplom.chatservice.service.RoleCacheService;
import com.diplom.chatservice.service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserEventsConsumerTest {

    @Mock FriendLinkProjectionService friendLinkProjectionService;
    @Mock ModerationBlocklistService moderationBlocklistService;
    @Mock RoleCacheService roleCacheService;
    @Mock RoomService roomService;
    @Mock StringRedisTemplate stringRedisTemplate;

    private UserEventsConsumer consumer;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Mirrors Spring Boot's auto-configured mapper: RoleUpdatedEvent carries an OffsetDateTime.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new UserEventsConsumer(friendLinkProjectionService, moderationBlocklistService,
                roleCacheService, roomService, objectMapper, stringRedisTemplate);
    }

    private void send(int roleId) {
        consumer.onUserEvent(
                "{\"userId\":\"" + userId + "\",\"roleId\":" + roleId + ",\"occurredAt\":\"2026-09-18T10:00:00Z\"}",
                "user.role-updated");
    }

    @ParameterizedTest
    @CsvSource({"1,GUEST", "2,FREE", "3,BASIC", "4,AUTHOR", "5,ADMIN"})
    void cachesEveryKnownRoleIncludingAdmin(int roleId, String expected) {
        send(roleId);
        verify(roleCacheService).putRole(userId, expected);
    }

    @Test
    void unknownRoleIdLeavesTheCacheUntouchedInsteadOfDowngradingToGuest() {
        send(99);
        verify(roleCacheService, never()).putRole(any(), anyString());
    }

    @Test
    void unparseablePayloadIsSwallowedAndCacheUntouched() {
        consumer.onUserEvent("not json", "user.role-updated");
        verifyNoInteractions(roleCacheService);
    }
}
