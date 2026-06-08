package com.diplom.billingservice.dto;

import java.time.OffsetDateTime;

public record SubscriptionResponse(
        String tier,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        boolean trialUsed
) {}
