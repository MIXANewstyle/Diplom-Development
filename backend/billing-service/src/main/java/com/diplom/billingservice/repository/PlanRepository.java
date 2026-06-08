package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Integer> {
    List<Plan> findAllByIsActiveTrueAndIsPublicTrue();
}
