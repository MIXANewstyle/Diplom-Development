package com.diplom.billingservice.payment;

import java.math.BigDecimal;

public record WebhookEvent(String providerPaymentId, WebhookStatus status, BigDecimal amount) {}
