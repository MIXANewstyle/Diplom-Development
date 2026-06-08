package com.diplom.billingservice.controller;

import com.diplom.billingservice.dto.PlanResponse;
import com.diplom.billingservice.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing/plans")
@RequiredArgsConstructor
public class BillingPlanController {

    private final PlanRepository planRepository;

    @GetMapping
    public List<PlanResponse> getActivePublicPlans() {
        return planRepository.findAllByIsActiveTrueAndIsPublicTrue()
                .stream()
                .map(plan -> new PlanResponse(
                        plan.getId(),
                        plan.getCode(),
                        plan.getTier().getName(),
                        plan.getDurationDays(),
                        plan.getPriceAmount(),
                        plan.getCurrency()
                ))
                .toList();
    }
}
