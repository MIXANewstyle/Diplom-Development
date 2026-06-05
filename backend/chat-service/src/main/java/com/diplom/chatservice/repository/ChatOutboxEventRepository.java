package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.ChatOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatOutboxEventRepository extends JpaRepository<ChatOutboxEvent, UUID> {
    
    List<ChatOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("DELETE FROM ChatOutboxEvent e WHERE e.status = 'PROCESSED' AND e.createdAt < :threshold")
    int deleteProcessedOlderThan(@Param("threshold") ZonedDateTime threshold);
}
