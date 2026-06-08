package com.diplom.billingservice.service;

import com.diplom.billingservice.dto.SubscriptionResponse;
import com.diplom.billingservice.entity.BillingAccount;
import com.diplom.billingservice.entity.Plan;
import com.diplom.billingservice.entity.Subscription;
import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.entity.TxnStatus;
import com.diplom.billingservice.entity.TxnType;
import com.diplom.billingservice.exception.ActiveSubscriptionExistsException;
import com.diplom.billingservice.exception.TrialAlreadyUsedException;
import com.diplom.billingservice.repository.BillingAccountRepository;
import com.diplom.billingservice.repository.PlanRepository;
import com.diplom.billingservice.repository.SubscriptionRepository;
import com.diplom.billingservice.repository.TransactionRepository;
import com.diplom.billingservice.repository.TxnStatusRepository;
import com.diplom.billingservice.repository.TxnTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrialService {

    private static final int TXN_TYPE_TRIAL_ID = 3;  // txn_types seed: TRIAL
    private static final int TXN_STATUS_SUCCESS_ID = 2;  // txn_statuses seed: SUCCESS

    private final BillingAccountRepository billingAccountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TransactionRepository transactionRepository;
    private final TxnTypeRepository txnTypeRepository;
    private final TxnStatusRepository txnStatusRepository;
    private final ActivationService activationService;
    private final BillingAccountService billingAccountService;

    @Transactional
    public SubscriptionResponse claimTrial(UUID userId) {
        // 0. Defensive account bootstrap (self-heals if USER_REGISTERED event was lost)
        billingAccountService.createIfAbsent(userId);

        // 1. Load account — trial_used check (§5.2 step 1)
        BillingAccount account = billingAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "BillingAccount missing after createIfAbsent — should be impossible"));

        if (account.isTrialUsed()) {
            throw new TrialAlreadyUsedException("Free trial has already been used");
        }

        // 2. Active-subscription guard (§5.2 step 2)
        subscriptionRepository.findByUserId(userId).ifPresent(sub -> {
            if ("ACTIVE".equals(sub.getStatus().getName())) {
                throw new ActiveSubscriptionExistsException(
                        "You already have an active subscription");
            }
        });

        // 3. Load the TRIAL_30 plan
        Plan trialPlan = planRepository.findByCode("TRIAL_30")
                .orElseThrow(() -> new IllegalStateException("TRIAL_30 plan not found in database"));

        // 4. Claim the trial — mark trial_used = true (§5.2 step 3, guarded by @Version)
        account.setTrialUsed(true);
        try {
            billingAccountRepository.save(account);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Concurrent request already set trial_used = true
            throw new TrialAlreadyUsedException("Free trial has already been used (concurrent request)");
        }

        // 5. Write the ledger entry (§5.2 step 3)
        Transaction txn = Transaction.builder()
                .userId(userId)
                .plan(trialPlan)
                .type(loadTxnTypeTrial())
                .status(loadTxnStatusSuccess())
                .baseAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .currency("RUB")
                .provider("stub")
                .build();
        txn = transactionRepository.save(txn);

        // 6. Activate (subscription upsert + outbox write) — all in this same @Transactional
        Subscription sub = activationService.activate(userId, trialPlan, txn);

        // 7. Build and return the subscription view
        boolean trialUsed = billingAccountRepository.findById(userId)
                .map(BillingAccount::isTrialUsed).orElse(true);
        return new SubscriptionResponse(
                sub.getTier().getName(),
                sub.getStatus().getName(),
                sub.getStartedAt().toOffsetDateTime(),
                sub.getExpiresAt().toOffsetDateTime(),
                trialUsed
        );
    }

    private TxnType loadTxnTypeTrial() {
        return txnTypeRepository.findById(TXN_TYPE_TRIAL_ID)
                .orElseThrow(() -> new IllegalStateException("TxnType TRIAL not found"));
    }

    private TxnStatus loadTxnStatusSuccess() {
        return txnStatusRepository.findById(TXN_STATUS_SUCCESS_ID)
                .orElseThrow(() -> new IllegalStateException("TxnStatus SUCCESS not found"));
    }
}
