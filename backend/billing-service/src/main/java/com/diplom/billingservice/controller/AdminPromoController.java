package com.diplom.billingservice.controller;

import com.diplom.billingservice.dto.PromoCreateRequest;
import com.diplom.billingservice.dto.PromoResponse;
import com.diplom.billingservice.dto.PromoUpdateRequest;
import com.diplom.billingservice.service.AdminPromoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/billing/promo")
@RequiredArgsConstructor
public class AdminPromoController {

    private final AdminPromoService adminPromoService;

    @PostMapping
    public ResponseEntity<PromoResponse> create(@Valid @RequestBody PromoCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminPromoService.create(req));
    }

    @GetMapping
    public ResponseEntity<List<PromoResponse>> list() {
        return ResponseEntity.ok(adminPromoService.list());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PromoResponse> update(@PathVariable UUID id, @RequestBody PromoUpdateRequest req) {
        return ResponseEntity.ok(adminPromoService.update(id, req));
    }
}
