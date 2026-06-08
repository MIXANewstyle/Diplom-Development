package com.diplom.billingservice.controller;

import com.diplom.billingservice.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final WebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody String rawBody) {
        webhookService.handleWebhook(headers, rawBody);
        return ResponseEntity.ok().build();
    }
}
