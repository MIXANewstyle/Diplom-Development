package com.diplom.userservice.repository;

import com.diplom.userservice.entity.AuthorFollow;
import com.diplom.userservice.entity.AuthorFollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuthorFollowRepository extends JpaRepository<AuthorFollow, AuthorFollowId> {
    boolean existsByFollowerIdAndAuthorId(UUID followerId, UUID authorId);

    @Modifying
    void deleteByFollowerIdAndAuthorId(UUID followerId, UUID authorId);
}
