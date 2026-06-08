package com.diplom.billingservice.payment;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentProvider {
    PaymentIntent createPayment(BigDecimal amount, String currency, PaymentMetadata meta);
    WebhookEvent parseAndVerify(Map<String, String> headers, String rawBody);
    RefundResult refund(String providerPaymentId, BigDecimal amount);
}
