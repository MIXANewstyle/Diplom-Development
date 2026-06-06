package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    @Query("""
        SELECT r FROM Room r
        WHERE r.id IN (
            SELECT rp.roomId FROM RoomParticipant rp WHERE rp.userId = :userId
        )
        ORDER BY r.createdAt DESC
        """)
    Page<Room> findRoomsByParticipantUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
        SELECT r FROM Room r
        WHERE r.statusId IN (3, 4) AND r.id IN (
            SELECT rp.roomId FROM RoomParticipant rp WHERE rp.userId = :userId
        )
        """)
    java.util.List<Room> findActiveOrEndingRoomsByParticipantUserId(@Param("userId") UUID userId);
}
