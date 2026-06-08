package com.diplom.billingservice.service;

import com.diplom.billingservice.repository.SubscriptionRepository;
import com.diplom.billingservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingScheduledJobs {

    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;
    private final SubscriptionLifecycleService subscriptionLifecycleService;

    @Value("${billing.payment.intent-ttl-minutes}")
    private long intentTtlMinutes;

    private static final int SUB_STATUS_ACTIVE_ID = 1;
    private static final int TXN_STATUS_PENDING_ID = 1;

    @Scheduled(cron = "${billing.jobs.expire-subscriptions-cron}")
    public void expireSubscriptions() {
        List<UUID> ids = subscriptionRepository.findExpiredActiveIds(SUB_STATUS_ACTIVE_ID, ZonedDateTime.now());
        if (ids.isEmpty()) return;
        int ok = 0;
        for (UUID id : ids) {
            try {
                subscriptionLifecycleService.expireSubscription(id);
                ok++;
            } catch (Exception ex) {
                log.error("Failed to expire subscription {}", id, ex);
            }
        }
        log.info("expireSubscriptions: {}/{} subscriptions downgraded to FREE", ok, ids.size());
    }

    @Scheduled(cron = "${billing.jobs.expire-stale-transactions-cron}")
    public void expireStalePendingTransactions() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(intentTtlMinutes);
        List<UUID> ids = transactionRepository.findStalePendingIds(TXN_STATUS_PENDING_ID, threshold);
        if (ids.isEmpty()) return;
        int ok = 0;
        for (UUID id : ids) {
            try {
                subscriptionLifecycleService.failAndCompensate(id);
                ok++;
            } catch (Exception ex) {
                log.error("Failed to fail stale transaction {}", id, ex);
            }
        }
        log.info("expireStalePendingTransactions: {}/{} stale intents failed", ok, ids.size());
    }
}
