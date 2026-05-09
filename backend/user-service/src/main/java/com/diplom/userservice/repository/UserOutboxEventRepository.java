package com.diplom.userservice.repository;

import com.diplom.userservice.entity.UserOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserOutboxEventRepository extends JpaRepository<UserOutboxEvent, UUID> {
    
    List<UserOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("DELETE FROM UserOutboxEvent e WHERE e.status = 'PROCESSED' AND e.createdAt < :threshold")
    int deleteProcessedOlderThan(ZonedDateTime threshold);
}
