package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByUserId(UUID userId);

    @Query("SELECT s.id FROM Subscription s WHERE s.status.id = :statusId AND s.expiresAt < :now")
    List<UUID> findExpiredActiveIds(@Param("statusId") int statusId, @Param("now") ZonedDateTime now);
}
