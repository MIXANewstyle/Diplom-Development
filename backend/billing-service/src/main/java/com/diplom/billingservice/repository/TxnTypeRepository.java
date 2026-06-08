package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.TxnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TxnTypeRepository extends JpaRepository<TxnType, Integer> {}
