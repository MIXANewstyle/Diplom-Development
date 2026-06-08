package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.SubTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubTierRepository extends JpaRepository<SubTier, Integer> {}
