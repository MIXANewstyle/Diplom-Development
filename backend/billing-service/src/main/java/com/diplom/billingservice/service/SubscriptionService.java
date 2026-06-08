package com.diplom.billingservice.service;

import com.diplom.billingservice.dto.SubscriptionResponse;
import com.diplom.billingservice.entity.BillingAccount;
import com.diplom.billingservice.entity.Subscription;
import com.diplom.billingservice.repository.BillingAccountRepository;
import com.diplom.billingservice.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingAccountRepository billingAccountRepository;

    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription(UUID userId) {
        boolean trialUsed = billingAccountRepository.findById(userId)
                .map(BillingAccount::isTrialUsed)
                .orElse(false);

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserId(userId);

        if (subscriptionOpt.isPresent()) {
            Subscription sub = subscriptionOpt.get();
            return new SubscriptionResponse(
                    sub.getTier() != null ? sub.getTier().getName() : null,
                    sub.getStatus() != null ? sub.getStatus().getName() : "NONE",
                    sub.getStartedAt() != null ? sub.getStartedAt().toOffsetDateTime() : null,
                    sub.getExpiresAt() != null ? sub.getExpiresAt().toOffsetDateTime() : null,
                    trialUsed
            );
        }

        return new SubscriptionResponse(null, "NONE", null, null, trialUsed);
    }
}
