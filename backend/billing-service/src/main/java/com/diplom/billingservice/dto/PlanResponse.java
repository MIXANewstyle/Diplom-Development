package com.diplom.billingservice.dto;

import java.math.BigDecimal;

public record PlanResponse(
        Integer id,
        String code,
        String tier,
        Integer durationDays,
        BigDecimal price,
        String currency
) {}
