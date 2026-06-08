package com.diplom.billingservice.controller;

import com.diplom.billingservice.dto.SubscriptionResponse;
import com.diplom.billingservice.service.TrialService;
import com.diplom.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/subscriptions")
@RequiredArgsConstructor
public class BillingSubscriptionController {

    private final TrialService trialService;

    @PostMapping("/trial")
    public ResponseEntity<SubscriptionResponse> claimTrial(
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID userId = user.getId();
        SubscriptionResponse response = trialService.claimTrial(userId);
        return ResponseEntity.ok(response);
    }
}
