package com.diplom.billingservice.service;

import com.diplom.billingservice.dto.SubscriptionResponse;
import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.repository.TransactionRepository;
import com.diplom.billingservice.repository.TxnStatusRepository;
import com.diplom.billingservice.repository.TxnTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GrantService {

    private final BillingAccountService billingAccountService;
    private final TransactionRepository transactionRepository;
    private final TxnTypeRepository txnTypeRepository;
    private final TxnStatusRepository txnStatusRepository;
    private final ActivationService activationService;
    private final SubscriptionService subscriptionService;

    private static final int TXN_TYPE_PURCHASE_ID = 1;
    private static final int TXN_STATUS_PENDING_ID = 1;

    @Transactional
    public SubscriptionResponse grant(UUID adminId, UUID userId, int days, String note) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be >= 1");
        }
        billingAccountService.createIfAbsent(userId);  // defensive — self-heal a missing anchor

        Transaction txn = Transaction.builder()
                .userId(userId)
                .plan(null)                       // grant is not tied to a purchasable plan (plan_id is nullable)
                .type(txnTypeRepository.findById(TXN_TYPE_PURCHASE_ID).orElseThrow(() -> new IllegalStateException("TxnType PURCHASE not found")))
                .status(txnStatusRepository.findById(TXN_STATUS_PENDING_ID).orElseThrow(() -> new IllegalStateException("TxnStatus PENDING not found")))
                .baseAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .currency("RUB")
                .provider("manual")               // marks the row as a manual grant (audit marker)
                .build();
        txn = transactionRepository.save(txn);

        activationService.activateForDays(userId, txn, days);   // upserts subscription, sets txn SUCCESS, emits SUBSCRIPTION_CHANGED{BASIC}

        log.info("Manual grant: admin={} granted BASIC for {} days to user={} (note: {})", adminId, days, userId, note);

        return subscriptionService.getMySubscription(userId);   // reuse the existing read mapping
    }
}
