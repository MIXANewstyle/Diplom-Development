package com.diplom.contentservice.repository;

import com.diplom.contentservice.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query("""
        SELECT c FROM Comment c
        WHERE c.postId = :postId AND c.parentId IS NULL
          AND (:afterCreatedAt IS NULL
               OR c.createdAt > :afterCreatedAt
               OR (c.createdAt = :afterCreatedAt AND c.id > :afterId))
        ORDER BY c.createdAt ASC, c.id ASC
    """)
    List<Comment> findRootCommentsAfter(
        @Param("postId") UUID postId,
        @Param("afterCreatedAt") OffsetDateTime afterCreatedAt,
        @Param("afterId") UUID afterId,
        Pageable pageable
    );

    @Query("""
        SELECT c FROM Comment c
        WHERE c.parentId = :parentId
          AND (:afterCreatedAt IS NULL
               OR c.createdAt > :afterCreatedAt
               OR (c.createdAt = :afterCreatedAt AND c.id > :afterId))
        ORDER BY c.createdAt ASC, c.id ASC
    """)
    List<Comment> findRepliesAfter(
        @Param("parentId") UUID parentId,
        @Param("afterCreatedAt") OffsetDateTime afterCreatedAt,
        @Param("afterId") UUID afterId,
        Pageable pageable
    );

    @Query("""
        SELECT c.parentId AS parentId, COUNT(c) AS cnt
        FROM Comment c
        WHERE c.parentId IN :rootIds
        GROUP BY c.parentId
    """)
    List<RepliesCountProjection> countRepliesGrouped(@Param("rootIds") List<UUID> rootIds);

    long countByParentId(UUID parentId);
}
