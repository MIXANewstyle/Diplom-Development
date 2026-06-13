package com.diplom.billingservice.service;

import com.diplom.billingservice.dto.TransactionResponse;
import com.diplom.billingservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<TransactionResponse> getMyTransactions(UUID userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(txn -> new TransactionResponse(
                        txn.getId(),
                        txn.getType() != null ? txn.getType().getName() : null,
                        txn.getStatus() != null ? txn.getStatus().getName() : null,
                        txn.getPlan() != null ? txn.getPlan().getCode() : null,
                        txn.getAmount(),
                        txn.getCurrency(),
                        txn.getCreatedAt() != null ? txn.getCreatedAt().toOffsetDateTime() : null
                ))
                .toList();
    }
}
