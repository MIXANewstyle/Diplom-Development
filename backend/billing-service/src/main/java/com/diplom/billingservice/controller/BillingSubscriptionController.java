package com.diplom.billingservice.controller;

import com.diplom.billingservice.dto.CheckoutRequest;
import com.diplom.billingservice.dto.CheckoutResponse;
import com.diplom.billingservice.dto.SubscriptionResponse;
import com.diplom.billingservice.service.CheckoutService;
import com.diplom.billingservice.service.TrialService;
import com.diplom.billingservice.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/subscriptions")
@RequiredArgsConstructor
public class BillingSubscriptionController {

    private final TrialService trialService;
    private final CheckoutService checkoutService;

    @PostMapping("/trial")
    public ResponseEntity<SubscriptionResponse> claimTrial(
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID userId = user.getId();
        SubscriptionResponse response = trialService.claimTrial(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CheckoutResponse response = checkoutService.checkout(user.getId(), request, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
