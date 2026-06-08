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

    @Query(value = """
        SELECT t FROM Transaction t
        WHERE (:userId IS NULL OR t.userId = :userId)
          AND (:statusId IS NULL OR t.status.id = :statusId)
          AND (:from IS NULL OR t.createdAt >= :from)
          AND (:to IS NULL OR t.createdAt <= :to)
        ORDER BY t.createdAt DESC
        """,
        countQuery = """
        SELECT COUNT(t) FROM Transaction t
        WHERE (:userId IS NULL OR t.userId = :userId)
          AND (:statusId IS NULL OR t.status.id = :statusId)
          AND (:from IS NULL OR t.createdAt >= :from)
          AND (:to IS NULL OR t.createdAt <= :to)
        """)
    org.springframework.data.domain.Page<Transaction> searchTransactions(
            @Param("userId") UUID userId,
            @Param("statusId") Integer statusId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to,
            org.springframework.data.domain.Pageable pageable);
}
