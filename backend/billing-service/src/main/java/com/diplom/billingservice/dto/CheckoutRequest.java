package com.diplom.billingservice.dto;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
    @NotNull Integer planId,
    String promoCode // TODO Step 5: promo compensation
) {}
