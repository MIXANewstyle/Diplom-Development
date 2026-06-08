package com.diplom.billingservice.service;

import com.diplom.billingservice.dto.PromoCreateRequest;
import com.diplom.billingservice.dto.PromoResponse;
import com.diplom.billingservice.dto.PromoUpdateRequest;
import com.diplom.billingservice.entity.DiscountType;
import com.diplom.billingservice.entity.PromoCode;
import com.diplom.billingservice.exception.PromoCodeAlreadyExistsException;
import com.diplom.billingservice.exception.PromoCodeNotFoundException;
import com.diplom.billingservice.repository.DiscountTypeRepository;
import com.diplom.billingservice.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPromoService {

    private final PromoCodeRepository promoCodeRepository;
    private final DiscountTypeRepository discountTypeRepository;

    @Transactional
    public PromoResponse create(PromoCreateRequest req) {
        String code = req.code().trim().toUpperCase();
        if (promoCodeRepository.findByCode(code).isPresent()) {
            throw new PromoCodeAlreadyExistsException("Promo code already exists: " + code);
        }

        String typeName = req.discountType().trim().toUpperCase();
        DiscountType discountType = discountTypeRepository.findByName(typeName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown discount type: " + typeName));

        if (req.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("discountValue must be > 0");
        }
        if (req.maxUses() < 1) {
            throw new IllegalArgumentException("maxUses must be >= 1");
        }
        if ("PERCENT".equals(typeName) && req.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("PERCENT discount cannot exceed 100");
        }

        ZonedDateTime validFrom = req.validFrom() != null ? req.validFrom().toZonedDateTime() : null;
        ZonedDateTime validUntil = req.validUntil() != null ? req.validUntil().toZonedDateTime() : null;

        PromoCode promo = PromoCode.builder()
                .code(code)
                .discountType(discountType)
                .discountValue(req.discountValue())
                .maxUses(req.maxUses())
                .usedCount(0)
                .validFrom(validFrom)
                .validUntil(validUntil)
                .isActive(true)
                .build();

        promo = promoCodeRepository.save(promo);
        return toResponse(promo);
    }

    @Transactional(readOnly = true)
    public List<PromoResponse> list() {
        return promoCodeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PromoResponse update(UUID id, PromoUpdateRequest req) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new PromoCodeNotFoundException("Promo code not found: " + id));

        if (req.isActive() != null) {
            promo.setIsActive(req.isActive());
        }
        if (req.maxUses() != null) {
            if (req.maxUses() < promo.getUsedCount()) {
                throw new IllegalArgumentException("Cannot lower max_uses below used_count");
            }
            promo.setMaxUses(req.maxUses());
        }
        if (req.validFrom() != null) {
            promo.setValidFrom(req.validFrom().toZonedDateTime());
        }
        if (req.validUntil() != null) {
            promo.setValidUntil(req.validUntil().toZonedDateTime());
        }

        promo = promoCodeRepository.save(promo);
        return toResponse(promo);
    }

    private PromoResponse toResponse(PromoCode p) {
        OffsetDateTime vf = p.getValidFrom() != null ? p.getValidFrom().toOffsetDateTime() : null;
        OffsetDateTime vu = p.getValidUntil() != null ? p.getValidUntil().toOffsetDateTime() : null;
        OffsetDateTime ca = p.getCreatedAt() != null ? p.getCreatedAt().toOffsetDateTime() : null;

        return new PromoResponse(
                p.getId(),
                p.getCode(),
                p.getDiscountType().getName(),
                p.getDiscountValue(),
                p.getMaxUses(),
                p.getUsedCount(),
                vf,
                vu,
                Boolean.TRUE.equals(p.getIsActive()),
                ca
        );
    }
}
