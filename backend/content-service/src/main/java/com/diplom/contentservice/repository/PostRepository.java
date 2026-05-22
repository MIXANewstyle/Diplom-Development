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

    @Query(value = """
        SELECT * FROM content_schema.posts p
        WHERE p.status_id = 2
          AND p.author_id = ANY(CAST(:authorIds AS uuid[]))
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
    List<Post> feedFollowing(
        @Param("authorIds") String[] authorIds,
        @Param("cursorPublishedAt") OffsetDateTime cursorPublishedAt,
        @Param("cursorId") UUID cursorId,
        @Param("tagIds") String[] tagIds,
        @Param("tagCount") int tagCount,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM content_schema.posts p
        WHERE p.author_id = CAST(:authorId AS uuid)
          AND p.status_id = 2
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
    List<Post> postsByAuthor(
        @Param("authorId") UUID authorId,
        @Param("cursorPublishedAt") OffsetDateTime cursorPublishedAt,
        @Param("cursorId") UUID cursorId,
        @Param("tagIds") String[] tagIds,
        @Param("tagCount") int tagCount,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT p.id AS postId,
               (ts_rank_cd(p.search_vector, query.q)
                + CASE WHEN :keywordCount > 0 AND p.keywords && CAST(:keywordsArr AS text[])
                       THEN 0.3 ELSE 0 END) AS score
        FROM content_schema.posts p,
             websearch_to_tsquery('russian', :q) AS query(q)
        WHERE p.status_id = 2
          AND ( p.search_vector @@ query.q
                OR (:keywordCount > 0 AND p.keywords && CAST(:keywordsArr AS text[])) )
          AND ( :cursorScore IS NULL
                OR (ts_rank_cd(p.search_vector, query.q)
                    + CASE WHEN :keywordCount > 0 AND p.keywords && CAST(:keywordsArr AS text[])
                           THEN 0.3 ELSE 0 END) < :cursorScore
                OR (ABS((ts_rank_cd(p.search_vector, query.q)
                         + CASE WHEN :keywordCount > 0 AND p.keywords && CAST(:keywordsArr AS text[])
                                THEN 0.3 ELSE 0 END) - :cursorScore) < 0.000001
                    AND (p.published_at < CAST(:cursorPublishedAt AS timestamptz)
                         OR (p.published_at = CAST(:cursorPublishedAt AS timestamptz) AND p.id < CAST(:cursorId AS uuid)))) )
          AND ( :tagCount = 0 OR p.id IN (
                  SELECT pt.post_id FROM content_schema.post_tags pt
                  WHERE pt.tag_id = ANY(CAST(:tagIds AS uuid[]))
                  GROUP BY pt.post_id
                  HAVING COUNT(DISTINCT pt.tag_id) = :tagCount
                ) )
          AND ( :authorCount = 0 OR p.author_id = ANY(CAST(:authorIds AS uuid[])) )
          AND ( CAST(:fromDate AS timestamptz) IS NULL OR p.published_at >= CAST(:fromDate AS timestamptz) )
          AND ( CAST(:toDate AS timestamptz) IS NULL OR p.published_at <= CAST(:toDate AS timestamptz) )
        ORDER BY
            (ts_rank_cd(p.search_vector, query.q)
             + CASE WHEN :keywordCount > 0 AND p.keywords && CAST(:keywordsArr AS text[])
                    THEN 0.3 ELSE 0 END) DESC,
            p.published_at DESC,
            p.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<PostSearchHit> searchPostIds(
        @Param("q") String q,
        @Param("keywordsArr") String[] keywordsArr,
        @Param("keywordCount") int keywordCount,
        @Param("cursorScore") Double cursorScore,
        @Param("cursorPublishedAt") OffsetDateTime cursorPublishedAt,
        @Param("cursorId") UUID cursorId,
        @Param("tagIds") String[] tagIds,
        @Param("tagCount") int tagCount,
        @Param("authorIds") String[] authorIds,
        @Param("authorCount") int authorCount,
        @Param("fromDate") OffsetDateTime fromDate,
        @Param("toDate") OffsetDateTime toDate,
        @Param("limit") int limit
    );
}
