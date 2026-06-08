package com.diplom.billingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PromoRedemptionId implements Serializable {

    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    @Column(name = "user_id")
    private UUID userId;
}
