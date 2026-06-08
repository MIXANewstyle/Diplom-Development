package com.diplom.billingservice.payment;

public record PaymentIntent(String providerPaymentId, String confirmationUrl) {}
