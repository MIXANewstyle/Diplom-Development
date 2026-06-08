package com.diplom.billingservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "discount_types", schema = "billing_schema")
@Getter
public class DiscountType {
    
    @Id
    private Integer id;
    
    private String name;
}
