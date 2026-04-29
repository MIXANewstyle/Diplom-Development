package com.diplom.userservice.repository;

import com.diplom.userservice.entity.Friendship;
import com.diplom.userservice.entity.FriendshipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {
}
