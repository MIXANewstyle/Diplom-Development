package com.diplom.billingservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminTransactionResponse(
        UUID id,
        UUID userId,
        String planCode,
        String type,
        String status,
        BigDecimal baseAmount,
        BigDecimal discountAmount,
        BigDecimal amount,
        String currency,
        UUID promoCodeId,
        String provider,
        String providerPaymentId,
        String idempotencyKey,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
