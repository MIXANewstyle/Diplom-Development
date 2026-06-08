package com.diplom.billingservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PromoCreateRequest(
        @NotBlank String code,
        @NotBlank String discountType,
        @NotNull BigDecimal discountValue,
        @NotNull @Min(1) Integer maxUses,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil
) {}
