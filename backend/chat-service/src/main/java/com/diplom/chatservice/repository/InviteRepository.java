package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InviteRepository extends JpaRepository<Invite, UUID> {
    java.util.Optional<Invite> findByToken(String token);
    
    java.util.List<Invite> findByRoomIdAndStatusId(UUID roomId, Integer statusId);
}
