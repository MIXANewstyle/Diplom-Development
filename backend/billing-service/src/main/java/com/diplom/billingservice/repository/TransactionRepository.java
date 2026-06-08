package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Transaction> findByProviderPaymentId(String providerPaymentId);
    Optional<Transaction> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Query("SELECT t.id FROM Transaction t WHERE t.status.id = :statusId AND t.createdAt < :threshold")
    List<UUID> findStalePendingIds(@Param("statusId") int statusId, @Param("threshold") ZonedDateTime threshold);
}
