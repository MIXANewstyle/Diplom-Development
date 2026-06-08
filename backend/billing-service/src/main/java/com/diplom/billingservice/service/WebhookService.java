package com.diplom.billingservice.service;

import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.exception.TransactionNotFoundException;
import com.diplom.billingservice.payment.PaymentProvider;
import com.diplom.billingservice.payment.WebhookEvent;
import com.diplom.billingservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final TransactionRepository transactionRepository;
    private final ActivationService activationService;
    private final PaymentProvider paymentProvider;
    private final SubscriptionLifecycleService subscriptionLifecycleService;

    @Transactional
    public void handleWebhook(Map<String, String> headers, String rawBody) {
        WebhookEvent event = paymentProvider.parseAndVerify(headers, rawBody);

        Optional<Transaction> txnOpt = transactionRepository.findByProviderPaymentId(event.providerPaymentId());
        if (txnOpt.isEmpty()) {
            log.warn("Webhook for unknown providerPaymentId={}, ignoring", event.providerPaymentId());
            return;
        }
        Transaction txn = txnOpt.get();

        String status = txn.getStatus().getName();
        if ("SUCCESS".equals(status) || "FAILED".equals(status) || "REFUNDED".equals(status)) {
            return;
        }

        switch (event.status()) {
            case SUCCEEDED -> activationService.activate(txn.getUserId(), txn.getPlan(), txn);
            case CANCELED -> subscriptionLifecycleService.failAndCompensate(txn.getId());
        }
    }

    @Transactional
    public void confirmStubSuccess(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
        String status = txn.getStatus().getName();
        if ("SUCCESS".equals(status) || "FAILED".equals(status) || "REFUNDED".equals(status)) {
            return;
        }
        activationService.activate(txn.getUserId(), txn.getPlan(), txn);
    }
}
