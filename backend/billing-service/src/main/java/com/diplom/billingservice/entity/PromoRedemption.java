package com.diplom.billingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "promo_redemptions", schema = "billing_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoRedemption implements Persistable<PromoRedemptionId> {

    @EmbeddedId
    private PromoRedemptionId id;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @CreationTimestamp
    @Column(name = "redeemed_at", updatable = false)
    private ZonedDateTime redeemedAt;

    @Override
    @org.springframework.data.annotation.Transient
    public boolean isNew() {
        return true;
    }
}
