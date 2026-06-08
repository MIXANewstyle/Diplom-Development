package com.diplom.billingservice.controller;

import com.diplom.billingservice.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/payments/stub")
@RequiredArgsConstructor
@Profile({"dev", "local"})
public class StubPaymentController {

    private final WebhookService webhookService;

    @PostMapping("/confirm/{transactionId}")
    public ResponseEntity<Void> confirm(@PathVariable UUID transactionId) {
        webhookService.confirmStubSuccess(transactionId);
        return ResponseEntity.ok().build();
    }
}
