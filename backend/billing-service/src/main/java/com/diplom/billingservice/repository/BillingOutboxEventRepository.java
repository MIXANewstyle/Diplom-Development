package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.BillingOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillingOutboxEventRepository extends JpaRepository<BillingOutboxEvent, UUID> {

    List<BillingOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("DELETE FROM BillingOutboxEvent e WHERE e.status = 'PROCESSED' AND e.createdAt < :threshold")
    int deleteProcessedOlderThan(ZonedDateTime threshold);
}
