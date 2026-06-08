package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.DiscountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiscountTypeRepository extends JpaRepository<DiscountType, Integer> {
    Optional<DiscountType> findByName(String name);
}
