package com.diplom.billingservice.repository;

import com.diplom.billingservice.entity.SubStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubStatusRepository extends JpaRepository<SubStatus, Integer> {}
