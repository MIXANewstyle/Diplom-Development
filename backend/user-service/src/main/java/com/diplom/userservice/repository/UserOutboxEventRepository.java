package com.diplom.userservice.repository;

import com.diplom.userservice.entity.UserOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserOutboxEventRepository extends JpaRepository<UserOutboxEvent, UUID> {
}
