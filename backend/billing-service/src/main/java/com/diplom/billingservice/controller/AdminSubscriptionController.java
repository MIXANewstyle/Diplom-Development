package com.diplom.billingservice.controller;

import com.diplom.billingservice.dto.GrantRequest;
import com.diplom.billingservice.dto.SubscriptionResponse;
import com.diplom.billingservice.security.CustomUserDetails;
import com.diplom.billingservice.service.GrantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/billing/subscriptions")
@RequiredArgsConstructor
public class AdminSubscriptionController {
    private final GrantService grantService;

    @PostMapping("/{userId}/grant")
    public ResponseEntity<SubscriptionResponse> grant(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable UUID userId,
            @Valid @RequestBody GrantRequest request) {
        return ResponseEntity.ok(grantService.grant(admin.getId(), userId, request.days(), request.note()));
    }
}
