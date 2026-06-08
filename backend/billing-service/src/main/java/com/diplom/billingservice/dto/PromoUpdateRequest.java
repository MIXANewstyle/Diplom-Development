package com.diplom.billingservice.dto;

import java.time.OffsetDateTime;

public record PromoUpdateRequest(
        Boolean isActive,
        Integer maxUses,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil
) {}
