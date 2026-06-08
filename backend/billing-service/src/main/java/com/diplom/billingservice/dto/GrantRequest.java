package com.diplom.billingservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GrantRequest(
        @NotNull(message = "Days must be provided")
        @Min(value = 1, message = "Days must be >= 1")
        Integer days,
        
        String note
) {}
