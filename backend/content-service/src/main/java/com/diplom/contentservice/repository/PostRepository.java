package com.diplom.contentservice.repository;

import com.diplom.contentservice.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    @Modifying
    @Query("UPDATE Post p SET p.upvotesCount = p.upvotesCount + :delta WHERE p.id = :postId")
    int applyUpvotesDelta(@Param("postId") UUID postId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE Post p SET p.commentsCount = p.commentsCount + :delta WHERE p.id = :postId")
    int applyCommentsDelta(@Param("postId") UUID postId, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE Post p SET p.viewsCount = p.viewsCount + :delta WHERE p.id = :postId")
    int applyViewsDelta(@Param("postId") UUID postId, @Param("delta") long delta);
}
