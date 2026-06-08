package com.diplom.billingservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sub_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubTier {

    @Id
    private Integer id;

    private String name;
}
