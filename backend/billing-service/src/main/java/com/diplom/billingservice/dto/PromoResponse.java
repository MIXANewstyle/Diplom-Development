package com.diplom.billingservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PromoResponse(
        UUID id,
        String code,
        String discountType,
        BigDecimal discountValue,
        Integer maxUses,
        Integer usedCount,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        boolean isActive,
        OffsetDateTime createdAt
) {}
