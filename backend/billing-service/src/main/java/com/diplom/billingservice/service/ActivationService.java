package com.diplom.billingservice.service;

import com.diplom.billingservice.entity.BillingOutboxEvent;
import com.diplom.billingservice.entity.Plan;
import com.diplom.billingservice.entity.SubStatus;
import com.diplom.billingservice.entity.SubTier;
import com.diplom.billingservice.entity.Subscription;
import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.entity.TxnStatus;
import com.diplom.billingservice.event.EventType;
import com.diplom.billingservice.event.SubscriptionChangedEvent;
import com.diplom.billingservice.outbox.OutboxEventFactory;
import com.diplom.billingservice.entity.PromoRedemptionId;
import com.diplom.billingservice.repository.BillingOutboxEventRepository;
import com.diplom.billingservice.repository.PromoRedemptionRepository;
import com.diplom.billingservice.repository.SubStatusRepository;
import com.diplom.billingservice.repository.SubTierRepository;
import com.diplom.billingservice.repository.SubscriptionRepository;
import com.diplom.billingservice.repository.TxnStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivationService {

    private static final int TIER_BASIC_ID = 1;  // sub_tiers: BASIC
    private static final int STATUS_ACTIVE_ID = 1;  // sub_statuses: ACTIVE
    private static final int TXN_STATUS_SUCCESS_ID = 2;  // txn_statuses: SUCCESS

    private final SubscriptionRepository subscriptionRepository;
    private final SubTierRepository subTierRepository;
    private final SubStatusRepository subStatusRepository;
    private final TxnStatusRepository txnStatusRepository;
    private final BillingOutboxEventRepository billingOutboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final PromoRedemptionRepository promoRedemptionRepository;

    @Transactional
    public Subscription activate(UUID userId, Plan plan, Transaction transaction) {
        return activateForDays(userId, transaction, plan.getDurationDays());
    }

    @Transactional
    public Subscription activateForDays(UUID userId, Transaction transaction, int durationDays) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime newExpiresAt;

        Optional<Subscription> existingOpt = subscriptionRepository.findByUserId(userId);
        Subscription sub;

        if (existingOpt.isEmpty()) {
            // First activation: create a new row
            SubTier basicTier = subTierRepository.findById(TIER_BASIC_ID)
                    .orElseThrow(() -> new IllegalStateException("SubTier BASIC not found"));
            SubStatus activeStatus = subStatusRepository.findById(STATUS_ACTIVE_ID)
                    .orElseThrow(() -> new IllegalStateException("SubStatus ACTIVE not found"));

            newExpiresAt = now.plusDays(durationDays);
            sub = Subscription.builder()
                    .userId(userId)
                    .tier(basicTier)
                    .status(activeStatus)
                    .startedAt(now)
                    .expiresAt(newExpiresAt)
                    .build();
        } else {
            sub = existingOpt.get();
            SubStatus currentStatus = sub.getStatus();

            if ("ACTIVE".equals(currentStatus.getName()) && !sub.getExpiresAt().isBefore(now)) {
                // Renewal — stack expires_at (§3.2, §0.1.4)
                newExpiresAt = sub.getExpiresAt().plusDays(durationDays);
                sub.setExpiresAt(newExpiresAt);
            } else {
                // EXPIRED or CANCELED — restart
                SubStatus activeStatus = subStatusRepository.findById(STATUS_ACTIVE_ID)
                        .orElseThrow(() -> new IllegalStateException("SubStatus ACTIVE not found"));
                newExpiresAt = now.plusDays(durationDays);
                sub.setStatus(activeStatus);
                sub.setStartedAt(now);
                sub.setExpiresAt(newExpiresAt);
            }
        }
        sub = subscriptionRepository.save(sub);

        // Finalize the ledger (§8.1 step 2): set the transaction's status to SUCCESS.
        transaction.setStatus(loadTxnStatusSuccess());
        // No explicit save needed: the Transaction entity is managed within this @Transactional boundary.

        // §8.1 step 3 — link the promo redemption to this transaction (paid path with promo)
        if (transaction.getPromoCodeId() != null) {
            promoRedemptionRepository.findById(new PromoRedemptionId(transaction.getPromoCodeId(), userId))
                    .ifPresent(r -> r.setTransactionId(transaction.getId()));
        }

        // Write SUBSCRIPTION_CHANGED to the outbox (§7.1)
        SubscriptionChangedEvent payload = new SubscriptionChangedEvent(
                userId,
                "BASIC",
                newExpiresAt.toOffsetDateTime(),
                OffsetDateTime.now()
        );
        BillingOutboxEvent outboxEvent = outboxEventFactory.create(EventType.SUBSCRIPTION_CHANGED, payload);
        billingOutboxEventRepository.save(outboxEvent);

        return sub;
    }

    private TxnStatus loadTxnStatusSuccess() {
        return txnStatusRepository.findById(TXN_STATUS_SUCCESS_ID)
                .orElseThrow(() -> new IllegalStateException("TxnStatus SUCCESS not found"));
    }
}
