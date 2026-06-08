package com.diplom.billingservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubscriptionChangedEvent(
        UUID userId,
        String newTier,
        OffsetDateTime expiresAt,
        OffsetDateTime occurredAt
) {}
