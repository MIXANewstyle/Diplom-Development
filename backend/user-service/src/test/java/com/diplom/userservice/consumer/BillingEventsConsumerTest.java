package com.diplom.userservice.consumer;

import com.diplom.userservice.entity.User;
import com.diplom.userservice.repository.UserRepository;
import com.diplom.userservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingEventsConsumerTest {

    @Mock UserService userService;
    @Mock UserRepository userRepository;

    private BillingEventsConsumer consumer;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new BillingEventsConsumer(userService, userRepository, new ObjectMapper());
    }

    private void withRole(int roleId) {
        User user = new User();
        user.setId(userId);
        user.setRoleId(roleId);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private void send(String tier) {
        consumer.onBillingEvent(
                "{\"userId\":\"" + userId + "\",\"newTier\":\"" + tier + "\",\"occurredAt\":\"2026-09-18T10:00:00Z\"}",
                "billing.subscription-changed");
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5}) // AUTHOR, ADMIN — clear the gate via the role hierarchy
    void expiryNeverDemotesRolesAboveBasic(int roleId) {
        withRole(roleId);
        send("FREE");
        verify(userService, never()).updateUserRole(any(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5})
    void purchaseNeverDemotesRolesAboveBasic(int roleId) {
        withRole(roleId);
        send("BASIC");
        verify(userService, never()).updateUserRole(any(), anyInt());
    }

    @Test
    void expiryDemotesBasicToFree() {
        withRole(3);
        send("FREE");
        verify(userService).updateUserRole(userId, 2);
    }

    @Test
    void purchasePromotesFreeToBasic() {
        withRole(2);
        send("BASIC");
        verify(userService).updateUserRole(userId, 3);
    }

    @Test
    void renewalOfAnAlreadyBasicUserIsANoOp() {
        withRole(3);
        send("BASIC");
        verify(userService, never()).updateUserRole(any(), anyInt());
    }

    @Test
    void unknownTierIsIgnoredWithoutTouchingTheUser() {
        send("PREMIUM");
        verifyNoInteractions(userService);
        verifyNoInteractions(userRepository);
    }

    @Test
    void unknownUserIsIgnored() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        send("BASIC");
        verify(userService, never()).updateUserRole(any(), anyInt());
    }
}
