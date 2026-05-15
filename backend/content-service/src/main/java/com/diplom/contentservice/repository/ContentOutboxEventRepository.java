package com.diplom.contentservice.repository;

import com.diplom.contentservice.entity.ContentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContentOutboxEventRepository extends JpaRepository<ContentOutboxEvent, UUID> {
    
    List<ContentOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("DELETE FROM ContentOutboxEvent e WHERE e.status = 'PROCESSED' AND e.createdAt < :threshold")
    int deleteProcessedOlderThan(@Param("threshold") ZonedDateTime threshold);
}
