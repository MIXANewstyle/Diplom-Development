package com.diplom.billingservice.service;

import com.diplom.billingservice.dto.AdminTransactionPageResponse;
import com.diplom.billingservice.dto.AdminTransactionResponse;
import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.entity.TxnStatus;
import com.diplom.billingservice.exception.TransactionNotFoundException;
import com.diplom.billingservice.repository.TransactionRepository;
import com.diplom.billingservice.repository.TxnStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTransactionService {

    private final TransactionRepository transactionRepository;
    private final TxnStatusRepository txnStatusRepository;

    @Transactional(readOnly = true)
    public AdminTransactionPageResponse search(UUID userId, String status, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        Integer statusId = null;
        if (status != null && !status.isBlank()) {
            statusId = txnStatusRepository.findByName(status.trim().toUpperCase())
                    .map(TxnStatus::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown status: " + status));
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> result = transactionRepository.searchTransactions(
                userId, statusId,
                from == null ? null : from.toZonedDateTime(),
                to == null ? null : to.toZonedDateTime(),
                pageable);
        List<AdminTransactionResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new AdminTransactionPageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminTransactionResponse getById(UUID id) {
        return transactionRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
    }

    private AdminTransactionResponse toResponse(Transaction t) {
        return new AdminTransactionResponse(
                t.getId(), t.getUserId(),
                t.getPlan() == null ? null : t.getPlan().getCode(),
                t.getType().getName(), t.getStatus().getName(),
                t.getBaseAmount(), t.getDiscountAmount(), t.getAmount(), t.getCurrency(),
                t.getPromoCodeId(), t.getProvider(), t.getProviderPaymentId(), t.getIdempotencyKey(),
                t.getCreatedAt().toOffsetDateTime(),
                t.getUpdatedAt() == null ? null : t.getUpdatedAt().toOffsetDateTime());
    }
}
