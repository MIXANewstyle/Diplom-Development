package com.diplom.billingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PromoValidationRequest(
        @NotNull Integer planId,
        @NotBlank String code
) {}
