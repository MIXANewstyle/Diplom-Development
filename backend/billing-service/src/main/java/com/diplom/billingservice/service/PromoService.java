package com.diplom.billingservice.service;

import com.diplom.billingservice.dto.PromoValidationResponse;
import com.diplom.billingservice.entity.DiscountType;
import com.diplom.billingservice.entity.Plan;
import com.diplom.billingservice.entity.PromoCode;
import com.diplom.billingservice.entity.PromoRedemptionId;
import com.diplom.billingservice.exception.*;
import com.diplom.billingservice.repository.PlanRepository;
import com.diplom.billingservice.repository.PromoCodeRepository;
import com.diplom.billingservice.repository.PromoRedemptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromoService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;
    private final PlanRepository planRepository;

    public record PromoCalc(PromoCode promoCode, DiscountType discountType, BigDecimal discountAmount, BigDecimal finalAmount) {}

    @Transactional(readOnly = true)
    public PromoCalc validate(Plan plan, String rawCode, UUID userId) {
        String code = rawCode.trim().toUpperCase();
        PromoCode promo = promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new PromoCodeNotFoundException("Promo code not found: " + code));
        
        if (!Boolean.TRUE.equals(promo.getIsActive())) {
            throw new PromoCodeInactiveException("Promo code is not active");
        }
        
        ZonedDateTime now = ZonedDateTime.now();
        if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) {
            throw new PromoCodeExpiredException("Promo code is not yet valid");
        }
        if (promo.getValidUntil() != null && now.isAfter(promo.getValidUntil())) {
            throw new PromoCodeExpiredException("Promo code has expired");
        }
        if (promo.getUsedCount() >= promo.getMaxUses()) {
            throw new PromoCodeExhaustedException("Promo code is fully redeemed");
        }
        if (promoRedemptionRepository.existsById(new PromoRedemptionId(promo.getId(), userId))) {
            throw new PromoAlreadyRedeemedException("Promo code already redeemed by this user");
        }

        BigDecimal base = plan.getPriceAmount();
        BigDecimal discount = computeDiscount(promo, base);
        BigDecimal finalAmount = base.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new PromoCalc(promo, promo.getDiscountType(), discount, finalAmount);
    }

    private BigDecimal computeDiscount(PromoCode promo, BigDecimal base) {
        String dt = promo.getDiscountType().getName();
        BigDecimal discount;
        if ("PERCENT".equals(dt)) {
            discount = base.multiply(promo.getDiscountValue())
                           .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else { // FIXED
            discount = promo.getDiscountValue().min(base);
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public PromoValidationResponse preview(Integer planId, String rawCode, UUID userId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + planId));
        try {
            PromoCalc calc = validate(plan, rawCode, userId);
            return new PromoValidationResponse(true, calc.discountType().getName(), calc.discountAmount(), calc.finalAmount());
        } catch (PromoException e) {
            // invalid promo → show the undiscounted price so the UI can still display it
            return new PromoValidationResponse(false, null, BigDecimal.ZERO,
                    plan.getPriceAmount().setScale(2, RoundingMode.HALF_UP));
        }
    }
}
