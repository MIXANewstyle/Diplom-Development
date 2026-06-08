package com.diplom.billingservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckoutResponse(
    UUID transactionId,
    String status,
    BigDecimal amount,
    String currency,
    String confirmationUrl
) {}
