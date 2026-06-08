package com.diplom.billingservice.service;

import com.diplom.billingservice.entity.PromoRedemptionId;
import com.diplom.billingservice.entity.SubStatus;
import com.diplom.billingservice.entity.Subscription;
import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.entity.TxnStatus;
import com.diplom.billingservice.event.EventType;
import com.diplom.billingservice.event.SubscriptionChangedEvent;
import com.diplom.billingservice.outbox.OutboxEventFactory;
import com.diplom.billingservice.repository.BillingOutboxEventRepository;
import com.diplom.billingservice.repository.PromoCodeRepository;
import com.diplom.billingservice.repository.PromoRedemptionRepository;
import com.diplom.billingservice.repository.SubStatusRepository;
import com.diplom.billingservice.repository.SubscriptionRepository;
import com.diplom.billingservice.repository.TransactionRepository;
import com.diplom.billingservice.repository.TxnStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionLifecycleService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubStatusRepository subStatusRepository;
    private final TransactionRepository transactionRepository;
    private final TxnStatusRepository txnStatusRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final BillingOutboxEventRepository billingOutboxEventRepository;

    private static final int SUB_STATUS_EXPIRED_ID = 3;
    private static final int TXN_STATUS_FAILED_ID = 3;
    private static final int SUB_STATUS_CANCELED_ID = 2;  // sub_statuses: CANCELED

    @Transactional
    public void cancelSubscription(UUID userId) {
        Subscription sub = subscriptionRepository.findByUserId(userId).orElse(null);
        if (sub == null || !"ACTIVE".equals(sub.getStatus().getName())) {
            return;  // nothing active to cancel — already downgraded
        }
        SubStatus canceled = subStatusRepository.findById(SUB_STATUS_CANCELED_ID)
                .orElseThrow(() -> new IllegalStateException("SubStatus CANCELED not found"));
        sub.setStatus(canceled);
        subscriptionRepository.save(sub);

        SubscriptionChangedEvent payload = new SubscriptionChangedEvent(
                userId, "FREE", sub.getExpiresAt().toOffsetDateTime(), OffsetDateTime.now());
        billingOutboxEventRepository.save(outboxEventFactory.create(EventType.SUBSCRIPTION_CHANGED, payload));
    }

    @Transactional
    public void expireSubscription(UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null || !"ACTIVE".equals(sub.getStatus().getName())) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        if (!sub.getExpiresAt().isBefore(now)) {
            return;
        }

        SubStatus expired = subStatusRepository.findById(SUB_STATUS_EXPIRED_ID)
                .orElseThrow(() -> new IllegalStateException("SubStatus EXPIRED not found"));
        sub.setStatus(expired);
        subscriptionRepository.save(sub);

        SubscriptionChangedEvent payload = new SubscriptionChangedEvent(
                sub.getUserId(),
                "FREE",
                sub.getExpiresAt().toOffsetDateTime(),
                OffsetDateTime.now()
        );
        billingOutboxEventRepository.save(outboxEventFactory.create(EventType.SUBSCRIPTION_CHANGED, payload));
    }

    @Transactional
    public void failAndCompensate(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId).orElse(null);
        if (txn == null || !"PENDING".equals(txn.getStatus().getName())) {
            return;
        }
        TxnStatus failed = txnStatusRepository.findById(TXN_STATUS_FAILED_ID)
                .orElseThrow(() -> new IllegalStateException("TxnStatus FAILED not found"));
        txn.setStatus(failed);

        if (txn.getPromoCodeId() != null) {
            promoCodeRepository.releaseOne(txn.getPromoCodeId());
            promoRedemptionRepository.deleteById(new PromoRedemptionId(txn.getPromoCodeId(), txn.getUserId()));
        }
    }
}
