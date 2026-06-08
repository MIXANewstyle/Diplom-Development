package com.diplom.billingservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String type,
        String status,
        String planCode,
        BigDecimal amount,
        String currency,
        OffsetDateTime createdAt
) {}
