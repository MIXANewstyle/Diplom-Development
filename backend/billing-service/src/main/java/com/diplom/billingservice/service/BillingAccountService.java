package com.diplom.billingservice.service;

import com.diplom.billingservice.entity.BillingAccount;
import com.diplom.billingservice.repository.BillingAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingAccountService {

    private final BillingAccountRepository billingAccountRepository;

    @Transactional
    public void createIfAbsent(UUID userId) {
        if (billingAccountRepository.existsById(userId)) {
            return;
        }
        try {
            BillingAccount account = BillingAccount.builder()
                    .userId(userId)
                    .trialUsed(false)
                    .build();
            billingAccountRepository.save(account);
            log.debug("Created billing account for user {}", userId);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Billing account for user {} already exists (concurrent creation)", userId);
        }
    }
}
