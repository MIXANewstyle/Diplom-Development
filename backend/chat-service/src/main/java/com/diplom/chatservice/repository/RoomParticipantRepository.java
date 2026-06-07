package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.RoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, UUID> {

    List<RoomParticipant> findByRoomId(UUID roomId);

    Optional<RoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);
    
    boolean existsByIdAndRoomId(UUID id, UUID roomId);

    @Modifying
    @Query("UPDATE RoomParticipant rp SET rp.lastSeenAt = :lastSeenAt WHERE rp.roomId = :roomId AND rp.id = :participantId")
    void updateLastSeenAt(@Param("roomId") UUID roomId, @Param("participantId") UUID participantId, @Param("lastSeenAt") OffsetDateTime lastSeenAt);
}
