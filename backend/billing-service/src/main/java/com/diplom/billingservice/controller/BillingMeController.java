package com.diplom.billingservice.controller;

import com.diplom.billingservice.dto.SubscriptionResponse;
import com.diplom.billingservice.dto.TransactionResponse;
import com.diplom.billingservice.security.CustomUserDetails;
import com.diplom.billingservice.service.SubscriptionService;
import com.diplom.billingservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing/me")
@RequiredArgsConstructor
public class BillingMeController {

    private final SubscriptionService subscriptionService;
    private final TransactionService transactionService;

    @GetMapping("/subscription")
    public SubscriptionResponse getMySubscription(@AuthenticationPrincipal CustomUserDetails user) {
        return subscriptionService.getMySubscription(user.getId());
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> getMyTransactions(@AuthenticationPrincipal CustomUserDetails user) {
        return transactionService.getMyTransactions(user.getId());
    }
}
