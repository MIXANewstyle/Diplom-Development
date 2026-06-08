package com.diplom.billingservice.payment;

import com.diplom.billingservice.exception.WebhookSignatureInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "billing.payment.provider", havingValue = "stub", matchIfMissing = true)
public class StubPaymentProvider implements PaymentProvider {

    private final ObjectMapper objectMapper;

    @Value("${billing.payment.stub.confirm-base-url}")
    private String confirmBaseUrl;

    @Value("${billing.payment.stub.webhook-secret}")
    private String webhookSecret;

    @Override
    public PaymentIntent createPayment(BigDecimal amount, String currency, PaymentMetadata meta) {
        String providerPaymentId = "stub_" + UUID.randomUUID();
        String confirmationUrl = confirmBaseUrl + "/api/v1/billing/payments/stub/confirm/" + meta.transactionId();
        return new PaymentIntent(providerPaymentId, confirmationUrl);
    }

    @Override
    public WebhookEvent parseAndVerify(Map<String, String> headers, String rawBody) {
        String signature = headers.entrySet().stream()
                .filter(entry -> "x-stub-signature".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (signature == null || !signature.equals(webhookSecret)) {
            throw new WebhookSignatureInvalidException("Invalid stub webhook signature");
        }

        try {
            JsonNode node = objectMapper.readTree(rawBody);
            String providerPaymentId = node.get("providerPaymentId").asText();
            WebhookStatus status = WebhookStatus.valueOf(node.get("status").asText());
            BigDecimal amount = node.hasNonNull("amount") ? new BigDecimal(node.get("amount").asText()) : null;
            return new WebhookEvent(providerPaymentId, status, amount);
        } catch (Exception e) {
            throw new WebhookSignatureInvalidException("Malformed stub webhook body");
        }
    }

    @Override
    public RefundResult refund(String providerPaymentId, BigDecimal amount) {
        return new RefundResult("stub_refund_" + UUID.randomUUID());
    }
}
