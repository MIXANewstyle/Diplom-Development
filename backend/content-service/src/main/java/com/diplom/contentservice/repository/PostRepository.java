package com.diplom.contentservice.repository;

import com.diplom.contentservice.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
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

    @Query(value = """
        SELECT * FROM content_schema.posts p
        WHERE p.status_id = 2
          AND ( CAST(:cursorPublishedAt AS timestamptz) IS NULL
                OR p.published_at < CAST(:cursorPublishedAt AS timestamptz)
                OR (p.published_at = CAST(:cursorPublishedAt AS timestamptz) AND p.id < CAST(:cursorId AS uuid)) )
          AND ( :tagCount = 0 OR p.id IN (
                  SELECT pt.post_id FROM content_schema.post_tags pt
                  WHERE pt.tag_id = ANY(CAST(:tagIds AS uuid[]))
                  GROUP BY pt.post_id
                  HAVING COUNT(DISTINCT pt.tag_id) = :tagCount
                ) )
        ORDER BY p.published_at DESC, p.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Post> feedNewest(
        @Param("cursorPublishedAt") OffsetDateTime cursorPublishedAt,
        @Param("cursorId") UUID cursorId,
        @Param("tagIds") String[] tagIds,
        @Param("tagCount") int tagCount,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM content_schema.posts p
        WHERE p.status_id = 2
          AND ( :cursorSortValue IS NULL
                OR p.upvotes_count < :cursorSortValue
                OR (p.upvotes_count = :cursorSortValue
                    AND (p.published_at < CAST(:cursorPublishedAt AS timestamptz)
                         OR (p.published_at = CAST(:cursorPublishedAt AS timestamptz) AND p.id < CAST(:cursorId AS uuid)))) )
          AND ( :tagCount = 0 OR p.id IN (
                  SELECT pt.post_id FROM content_schema.post_tags pt
                  WHERE pt.tag_id = ANY(CAST(:tagIds AS uuid[]))
                  GROUP BY pt.post_id
                  HAVING COUNT(DISTINCT pt.tag_id) = :tagCount
                ) )
        ORDER BY p.upvotes_count DESC, p.published_at DESC, p.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Post> feedMostLiked(
        @Param("cursorSortValue") Long cursorSortValue,
        @Param("cursorPublishedAt") OffsetDateTime cursorPublishedAt,
        @Param("cursorId") UUID cursorId,
        @Param("tagIds") String[] tagIds,
        @Param("tagCount") int tagCount,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM content_schema.posts p
        WHERE p.status_id = 2
          AND ( :cursorSortValue IS NULL
                OR p.comments_count < :cursorSortValue
                OR (p.comments_count = :cursorSortValue
                    AND (p.published_at < CAST(:cursorPublishedAt AS timestamptz)
                         OR (p.published_at = CAST(:cursorPublishedAt AS timestamptz) AND p.id < CAST(:cursorId AS uuid)))) )
          AND ( :tagCount = 0 OR p.id IN (
                  SELECT pt.post_id FROM content_schema.post_tags pt
                  WHERE pt.tag_id = ANY(CAST(:tagIds AS uuid[]))
                  GROUP BY pt.post_id
                  HAVING COUNT(DISTINCT pt.tag_id) = :tagCount
                ) )
        ORDER BY p.comments_count DESC, p.published_at DESC, p.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Post> feedMostCommented(
        @Param("cursorSortValue") Long cursorSortValue,
        @Param("cursorPublishedAt") OffsetDateTime cursorPublishedAt,
        @Param("cursorId") UUID cursorId,
        @Param("tagIds") String[] tagIds,
        @Param("tagCount") int tagCount,
        @Param("limit") int limit
    );
}
