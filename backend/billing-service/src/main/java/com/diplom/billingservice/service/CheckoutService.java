package com.diplom.billingservice.service;

import com.diplom.billingservice.dto.CheckoutRequest;
import com.diplom.billingservice.dto.CheckoutResponse;
import com.diplom.billingservice.entity.Plan;
import com.diplom.billingservice.entity.Transaction;
import com.diplom.billingservice.exception.PlanInactiveException;
import com.diplom.billingservice.exception.PlanNotFoundException;
import com.diplom.billingservice.payment.PaymentIntent;
import com.diplom.billingservice.payment.PaymentMetadata;
import com.diplom.billingservice.payment.PaymentProvider;
import com.diplom.billingservice.repository.PlanRepository;
import com.diplom.billingservice.repository.SubscriptionRepository;
import com.diplom.billingservice.repository.TransactionRepository;
import com.diplom.billingservice.repository.TxnStatusRepository;
import com.diplom.billingservice.repository.TxnTypeRepository;
import com.diplom.billingservice.entity.PromoRedemption;
import com.diplom.billingservice.entity.PromoRedemptionId;
import com.diplom.billingservice.exception.PromoAlreadyRedeemedException;
import com.diplom.billingservice.exception.PromoCodeExhaustedException;
import com.diplom.billingservice.repository.PromoCodeRepository;
import com.diplom.billingservice.repository.PromoRedemptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final PlanRepository planRepository;
    private final TransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TxnTypeRepository txnTypeRepository;
    private final TxnStatusRepository txnStatusRepository;
    private final ActivationService activationService;
    private final PaymentProvider paymentProvider;
    private final PromoService promoService;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository promoRedemptionRepository;

    @Value("${billing.payment.provider}")
    private String providerName;

    @Value("${billing.payment.stub.confirm-base-url}")
    private String confirmBaseUrl;

    private static final int TXN_TYPE_PURCHASE_ID = 1;
    private static final int TXN_TYPE_RENEWAL_ID = 2;
    private static final int TXN_STATUS_PENDING_ID = 1;

    @Transactional
    public CheckoutResponse checkout(UUID userId, CheckoutRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> existingTxn = transactionRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existingTxn.isPresent()) {
                Transaction txn = existingTxn.get();
                if ("SUCCESS".equals(txn.getStatus().getName())) {
                    return new CheckoutResponse(txn.getId(), "ACTIVATED", null, null, null);
                } else {
                    String confirmationUrl = confirmBaseUrl + "/api/v1/billing/payments/" + providerName + "/confirm/" + txn.getId();
                    return new CheckoutResponse(txn.getId(), "REQUIRES_PAYMENT", txn.getAmount(), txn.getCurrency(), confirmationUrl);
                }
            }
        }

        Plan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + request.planId()));

        if (!plan.isActive() || !plan.isPublic()) {
            throw new PlanInactiveException("Plan is not available for purchase");
        }

        BigDecimal baseAmount = plan.getPriceAmount();
        BigDecimal discountAmount = BigDecimal.ZERO;
        UUID appliedPromoId = null;

        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            PromoService.PromoCalc calc = promoService.validate(plan, request.promoCode(), userId);
            UUID promoId = calc.promoCode().getId();

            // Atomic reservation (§5.1.3) — conditional UPDATE, no table lock
            int reserved = promoCodeRepository.reserveOne(promoId);
            if (reserved == 0) {
                throw new PromoCodeExhaustedException("Promo code is fully redeemed");
            }

            // Single-use-per-user guard — real INSERT (Persistable), catch the PK conflict
            try {
                PromoRedemption redemption = PromoRedemption.builder()
                        .id(new PromoRedemptionId(promoId, userId))
                        .build();
                promoRedemptionRepository.saveAndFlush(redemption);   // saveAndFlush forces the INSERT to execute now
            } catch (DataIntegrityViolationException dup) {
                throw new PromoAlreadyRedeemedException("Promo code already redeemed by this user");
            }

            discountAmount = calc.discountAmount();
            appliedPromoId = promoId;
        }

        BigDecimal finalAmount = baseAmount.subtract(discountAmount).max(BigDecimal.ZERO);

        boolean hasActive = subscriptionRepository.findByUserId(userId)
                .map(s -> "ACTIVE".equals(s.getStatus().getName()))
                .orElse(false);
        int typeId = hasActive ? TXN_TYPE_RENEWAL_ID : TXN_TYPE_PURCHASE_ID;

        Transaction txn = Transaction.builder()
                .userId(userId)
                .plan(plan)
                .type(txnTypeRepository.findById(typeId).orElseThrow(() -> new IllegalStateException("TxnType not found")))
                .status(txnStatusRepository.findById(TXN_STATUS_PENDING_ID).orElseThrow(() -> new IllegalStateException("TxnStatus PENDING not found")))
                .baseAmount(baseAmount)
                .discountAmount(discountAmount)
                .amount(finalAmount)
                .currency("RUB")
                .promoCodeId(appliedPromoId)
                .provider(providerName)
                .idempotencyKey(idempotencyKey)
                .build();
        txn = transactionRepository.save(txn);

        if (finalAmount.compareTo(BigDecimal.ZERO) == 0) {
            activationService.activate(userId, plan, txn);
            return new CheckoutResponse(txn.getId(), "ACTIVATED", null, null, null);
        }

        PaymentIntent intent = paymentProvider.createPayment(
                finalAmount, "RUB", new PaymentMetadata(txn.getId(), userId, plan.getCode()));
        txn.setProviderPaymentId(intent.providerPaymentId());
        transactionRepository.save(txn);

        return new CheckoutResponse(txn.getId(), "REQUIRES_PAYMENT", finalAmount, "RUB", intent.confirmationUrl());
    }
}
