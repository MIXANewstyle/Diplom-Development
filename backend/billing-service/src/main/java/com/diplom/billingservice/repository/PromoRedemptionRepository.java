package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.PromoRedemption;
import com.diplom.billingservice.entity.PromoRedemptionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, PromoRedemptionId> {
}
