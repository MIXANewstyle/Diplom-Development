package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByCode(String code);

    @Modifying
    @Query("UPDATE PromoCode p SET p.usedCount = p.usedCount + 1 WHERE p.id = :id AND p.usedCount < p.maxUses")
    int reserveOne(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE PromoCode p SET p.usedCount = p.usedCount - 1 WHERE p.id = :id AND p.usedCount > 0")
    int releaseOne(@Param("id") UUID id);
}
