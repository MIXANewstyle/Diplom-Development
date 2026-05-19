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

    // Note: the `:afterCreatedAt IS NULL` branch from the spec is intentionally
    // omitted here. Callers MUST supply non-null sentinel values when no cursor
    // is present (see CommentService.CURSOR_EPOCH / CURSOR_MIN_UUID). PostgreSQL
    // cannot determine the JDBC type of a parameter that appears only in an
    // `? IS NULL` predicate at Parse time, which causes
    // "could not determine data type of parameter $N". Restricting the parameter
    // to comparison contexts (`c.createdAt > ?`, `c.id > ?`) lets the planner
    // infer the type from the column.
    @Query("""
        SELECT c FROM Comment c
        WHERE c.postId = :postId AND c.parentId IS NULL
          AND (c.createdAt > :afterCreatedAt
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
          AND (c.createdAt > :afterCreatedAt
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
