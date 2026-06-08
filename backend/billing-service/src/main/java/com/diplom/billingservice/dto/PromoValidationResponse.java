package com.diplom.billingservice.dto;

import java.math.BigDecimal;

public record PromoValidationResponse(
        boolean valid,
        String discountType,
        BigDecimal discountAmount,
        BigDecimal finalAmount
) {}
