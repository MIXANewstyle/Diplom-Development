package com.diplom.billingservice.service;

import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.exception.TransactionNotFoundException;
import com.diplom.billingservice.exception.TransactionNotRefundableException;
import com.diplom.billingservice.provider.PaymentProvider;
import com.diplom.billingservice.provider.RefundResult;
import com.diplom.billingservice.repository.TransactionRepository;
import com.diplom.billingservice.repository.TxnStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final TransactionRepository transactionRepository;
    private final TxnStatusRepository txnStatusRepository;
    private final PaymentProvider paymentProvider;
    private final SubscriptionLifecycleService subscriptionLifecycleService;

    private static final int TXN_STATUS_REFUNDED_ID = 4;  // txn_statuses: REFUNDED

    @Transactional
    public void refund(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        if (!"SUCCESS".equals(txn.getStatus().getName())) {
            throw new TransactionNotRefundableException(
                    "Only SUCCESS transactions can be refunded; current status: " + txn.getStatus().getName());
        }

        // Provider-side refund (real gateways only). No external payment exists for trial/grant rows.
        if (txn.getProviderPaymentId() != null) {
            RefundResult result = paymentProvider.refund(txn.getProviderPaymentId(), txn.getAmount());  // may throw PaymentProviderException (502)
            log.info("Provider refund issued for transaction {} (refundId={})", transactionId, result.refundId());
        } else {
            log.info("Refund for transaction {} has no provider payment id (trial/grant) — skipping provider call", transactionId);
        }

        // §9.4 ledger: mark the original transaction REFUNDED (terminal; keeps webhooks idempotent)
        txn.setStatus(txnStatusRepository.findById(TXN_STATUS_REFUNDED_ID)
                .orElseThrow(() -> new IllegalStateException("TxnStatus REFUNDED not found")));

        // §9.4 saga: cancel the subscription + emit SUBSCRIPTION_CHANGED{newTier:"FREE"}
        subscriptionLifecycleService.cancelSubscription(txn.getUserId());
    }
}
