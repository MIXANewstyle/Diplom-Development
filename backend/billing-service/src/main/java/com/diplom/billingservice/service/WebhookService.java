package com.diplom.billingservice.service;

import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.exception.TransactionNotFoundException;
import com.diplom.billingservice.payment.PaymentProvider;
import com.diplom.billingservice.payment.WebhookEvent;
import com.diplom.billingservice.entity.PromoRedemptionId;
import com.diplom.billingservice.repository.PromoCodeRepository;
import com.diplom.billingservice.repository.PromoRedemptionRepository;
import com.diplom.billingservice.repository.TransactionRepository;
import com.diplom.billingservice.repository.TxnStatusRepository;
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
    private final TxnStatusRepository txnStatusRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;

    private static final int TXN_STATUS_FAILED_ID = 3;

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
            case CANCELED -> {
                txn.setStatus(txnStatusRepository.findById(TXN_STATUS_FAILED_ID)
                        .orElseThrow(() -> new IllegalStateException("TxnStatus FAILED not found")));
                // §8.3 — local promo compensation: free the reserved use so the user can retry
                if (txn.getPromoCodeId() != null) {
                    promoCodeRepository.releaseOne(txn.getPromoCodeId());
                    promoRedemptionRepository.deleteById(new PromoRedemptionId(txn.getPromoCodeId(), txn.getUserId()));
                }
            }
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
