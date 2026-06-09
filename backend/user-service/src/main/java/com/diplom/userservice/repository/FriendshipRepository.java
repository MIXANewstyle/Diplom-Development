package com.diplom.userservice.repository;

import com.diplom.userservice.entity.Friendship;
import com.diplom.userservice.entity.FriendshipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {
    Optional<Friendship> findByRequesterIdAndAddresseeId(UUID requesterId, UUID addresseeId);

    @Modifying
    void deleteByRequesterIdAndAddresseeId(UUID requesterId, UUID addresseeId);

    List<Friendship> findAllByRequesterIdOrAddresseeId(UUID requesterId, UUID addresseeId);
}
