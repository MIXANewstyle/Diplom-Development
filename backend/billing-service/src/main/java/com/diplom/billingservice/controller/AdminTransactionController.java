package com.diplom.billingservice.controller;

import com.diplom.billingservice.dto.AdminTransactionPageResponse;
import com.diplom.billingservice.dto.AdminTransactionResponse;
import com.diplom.billingservice.service.AdminTransactionService;
import com.diplom.billingservice.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/billing/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {
    private final AdminTransactionService adminTransactionService;
    private final RefundService refundService;

    @GetMapping
    public ResponseEntity<AdminTransactionPageResponse> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminTransactionService.search(userId, status, from, to, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminTransactionResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminTransactionService.getById(id));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<Void> refund(@PathVariable UUID id) {
        refundService.refund(id);
        return ResponseEntity.ok().build();
    }
}
