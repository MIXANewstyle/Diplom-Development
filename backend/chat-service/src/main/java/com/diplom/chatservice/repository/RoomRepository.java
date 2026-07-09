package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findWithLockById(@Param("id") UUID id);

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

    @Query("""
        SELECT r FROM Room r
        WHERE r.statusId = 5
          AND r.runningSummary IS NOT NULL
          AND r.id IN (
              SELECT rp.roomId FROM RoomParticipant rp WHERE rp.userId = :userId
          )
        ORDER BY r.createdAt DESC
        """)
    Page<Room> findSeedEligibleRooms(@Param("userId") UUID userId, Pageable pageable);
    @Query("""
        SELECT COUNT(r) FROM Room r
        WHERE r.statusId IN (1, 2, 3, 4) AND r.id IN (
            SELECT rp.roomId FROM RoomParticipant rp WHERE rp.userId = :userId
        )
        """)
    int countActiveOrEndingRoomsByParticipantUserId(@Param("userId") UUID userId);

    int countByStatusId(Integer statusId);

    @Query("SELECT r FROM Room r WHERE r.statusId IN (1, 2) AND r.createdAt < :threshold")
    Page<Room> findExpiredCandidates(@Param("threshold") OffsetDateTime threshold, Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.statusId IN (3, 4)")
    Page<Room> findAbandonedCandidates(Pageable pageable);

    @Modifying
    @Query("UPDATE Room r SET r.seedContextRoomId = null WHERE r.seedContextRoomId = :roomId")
    void nullifySeedContextReferences(@Param("roomId") UUID roomId);
}
