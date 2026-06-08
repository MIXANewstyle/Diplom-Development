package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.TxnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TxnStatusRepository extends JpaRepository<TxnStatus, Integer> {
    Optional<TxnStatus> findByName(String name);
}
