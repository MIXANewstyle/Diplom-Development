package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.FriendLink;
import com.diplom.chatservice.entity.FriendLinkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendLinkRepository extends JpaRepository<FriendLink, FriendLinkId> {
}
