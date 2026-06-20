package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InviteRepository extends JpaRepository<Invite, UUID> {
    java.util.Optional<Invite> findByToken(String token);
    
    java.util.List<Invite> findByRoomIdAndStatusId(UUID roomId, Integer statusId);

    @Modifying
    @Query("UPDATE Invite i SET i.statusId = :newStatusId WHERE i.roomId = :roomId AND i.statusId = :currentStatusId")
    void updateStatusByRoomId(@Param("roomId") UUID roomId, @Param("currentStatusId") Integer currentStatusId, @Param("newStatusId") Integer newStatusId);

    void deleteByRoomId(UUID roomId);
}
