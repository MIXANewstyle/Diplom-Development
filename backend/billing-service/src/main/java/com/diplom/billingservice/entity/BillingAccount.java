package com.diplom.billingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_accounts", schema = "billing_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAccount {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "trial_used")
    private boolean trialUsed;

    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Version
    private Integer version;
}
