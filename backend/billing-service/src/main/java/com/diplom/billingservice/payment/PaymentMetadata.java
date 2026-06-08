package com.diplom.billingservice.payment;

import java.util.UUID;

public record PaymentMetadata(UUID transactionId, UUID userId, String planCode) {}
