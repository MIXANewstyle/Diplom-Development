package com.diplom.billingservice.controller;

import com.diplom.billingservice.dto.PromoValidationRequest;
import com.diplom.billingservice.dto.PromoValidationResponse;
import com.diplom.billingservice.security.CustomUserDetails;
import com.diplom.billingservice.service.PromoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/promo")
@RequiredArgsConstructor
public class BillingPromoController {

    private final PromoService promoService;

    @PostMapping("/validate")
    public ResponseEntity<PromoValidationResponse> validate(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody PromoValidationRequest request) {
        return ResponseEntity.ok(promoService.preview(request.planId(), request.code(), user.getId()));
    }
}
