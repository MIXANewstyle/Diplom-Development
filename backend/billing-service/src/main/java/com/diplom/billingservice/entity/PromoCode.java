package com.diplom.billingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "promo_codes", schema = "billing_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, length = 64)
    private String code;

    @ManyToOne
    @JoinColumn(name = "discount_type_id")
    private DiscountType discountType;

    @Column(name = "discount_value")
    private BigDecimal discountValue;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count")
    private Integer usedCount;

    @Column(name = "valid_from")
    private ZonedDateTime validFrom;

    @Column(name = "valid_until")
    private ZonedDateTime validUntil;

    @Column(name = "is_active")
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Version
    private Integer version;

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
