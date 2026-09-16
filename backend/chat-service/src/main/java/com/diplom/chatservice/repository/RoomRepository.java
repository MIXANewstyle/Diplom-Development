package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
        WHERE (r.soloModeId IS NULL OR r.soloModeId <> 2)
          AND r.id IN (
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
          AND (r.soloModeId IS NULL OR r.soloModeId <> 2)
          AND r.id IN (
              SELECT rp.roomId FROM RoomParticipant rp WHERE rp.userId = :userId
          )
        ORDER BY r.createdAt DESC
        """)
    Page<Room> findSeedEligibleRooms(@Param("userId") UUID userId, Pageable pageable);
    @Query("""
        SELECT COUNT(r) FROM Room r
        WHERE r.statusId IN (1, 2, 3, 4)
          AND (r.soloModeId IS NULL OR r.soloModeId <> 2)
          AND r.id IN (
            SELECT rp.roomId FROM RoomParticipant rp WHERE rp.userId = :userId
        )
        """)
    int countActiveOrEndingRoomsByParticipantUserId(@Param("userId") UUID userId);

    int countByStatusId(Integer statusId);

    @Query("SELECT r FROM Room r WHERE r.statusId IN (1, 2) AND r.createdAt < :threshold")
    Page<Room> findExpiredCandidates(@Param("threshold") OffsetDateTime threshold, Pageable pageable);

    /**
     * ENDING rooms only (status 4). ACTIVE (3) is excluded so a dialogue where
     * participants are merely away (tab hidden / brief disconnect) is not
     * abandoned after {@code chat.sweeps.abandonment-timeout}; guest recovery
     * then remains possible until the guest JWT / room end.
     */
    @Query("SELECT r FROM Room r WHERE r.statusId = 4")
    Page<Room> findAbandonedCandidates(Pageable pageable);

    @Modifying
    @Query("UPDATE Room r SET r.seedContextRoomId = null WHERE r.seedContextRoomId = :roomId")
    void nullifySeedContextReferences(@Param("roomId") UUID roomId);

    // ==================== Diary (solo_mode_id = 2) ====================

    Optional<Room> findByOwnerUserIdAndDiaryDate(UUID ownerUserId, LocalDate diaryDate);

    java.util.List<Room> findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(
            UUID ownerUserId, LocalDate from, LocalDate to);

    /** ACTIVE diary rooms whose date is at or before {@code threshold} — candidates for closing. */
    @Query("SELECT r FROM Room r WHERE r.soloModeId = 2 AND r.statusId = 3 AND r.diaryDate <= :threshold ORDER BY r.diaryDate ASC")
    Page<Room> findDiaryRoomsToArchive(@Param("threshold") LocalDate threshold, Pageable pageable);

    /** ARCHIVED diary rooms that still have unsummarized turns (summary catch-up). */
    @Query("""
        SELECT r FROM Room r
        WHERE r.soloModeId = 2 AND r.statusId = 5
          AND EXISTS (
              SELECT t FROM Turn t
              WHERE t.roomId = r.id AND t.roleId <> 3
                AND t.seq > COALESCE(r.summarizedThroughSeq, 0)
          )
        ORDER BY r.diaryDate ASC
        """)
    Page<Room> findArchivedDiaryRoomsNeedingSummary(Pageable pageable);

    /** Distinct owners that have diary rooms dated inside [from, to]. */
    @Query("SELECT DISTINCT r.ownerUserId FROM Room r WHERE r.soloModeId = 2 AND r.diaryDate BETWEEN :from AND :to")
    java.util.List<UUID> findDiaryOwnersWithEntriesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
