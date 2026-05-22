package com.diplom.contentservice.feed;

import com.diplom.contentservice.dto.CounterDeltas;
import com.diplom.contentservice.dto.FeedPageResponse;
import com.diplom.contentservice.dto.PostResponse;
import com.diplom.contentservice.dto.UserBatchResponse;
import com.diplom.contentservice.entity.Post;
import com.diplom.contentservice.repository.PostRepository;
import com.diplom.contentservice.repository.PostSearchHit;
import com.diplom.contentservice.service.CounterService;
import com.diplom.contentservice.service.ModerationBlocklistService;
import com.diplom.contentservice.service.PostMapper;
import com.diplom.contentservice.service.ProfileCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final double OVERFETCH_FACTOR = 1.25;

    private final PostRepository postRepository;
    private final ProfileCacheService profileCacheService;
    private final CounterService counterService;
    private final PostMapper postMapper;
    private final ModerationBlocklistService moderationBlocklistService;

    @Transactional(readOnly = true)
    public FeedPageResponse search(
        String q,
        List<UUID> tagIds,
        List<UUID> authorIds,
        OffsetDateTime from,
        OffsetDateTime to,
        String cursor,
        Integer pageSize
    ) {
        if (q == null || q.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'q' is required");
        }

        int effectiveSize = clampPageSize(pageSize);
        int fetchLimit = (int) Math.ceil(effectiveSize * OVERFETCH_FACTOR) + 1;

        SearchCursor c = SearchCursor.decode(cursor);

        List<String> keywords = extractKeywords(q);
        String[] keywordsArr = keywords.toArray(new String[0]);
        int keywordCount = keywords.size();

        String[] tagIdArr;
        int tagCount;
        if (tagIds == null || tagIds.isEmpty()) {
            tagIdArr = new String[0];
            tagCount = 0;
        } else {
            tagIdArr = tagIds.stream().map(UUID::toString).toArray(String[]::new);
            tagCount = tagIds.size();
        }

        String[] authorIdArr;
        int authorCount;
        if (authorIds == null || authorIds.isEmpty()) {
            authorIdArr = new String[0];
            authorCount = 0;
        } else {
            authorIdArr = authorIds.stream().map(UUID::toString).toArray(String[]::new);
            authorCount = authorIds.size();
        }

        List<PostSearchHit> hits = postRepository.searchPostIds(
            q.trim(), keywordsArr, keywordCount,
            c == null ? null : c.score(),
            c == null ? null : c.publishedAt(),
            c == null ? null : c.id(),
            tagIdArr, tagCount,
            authorIdArr, authorCount,
            from, to,
            fetchLimit
        );

        if (hits.isEmpty()) {
            return new FeedPageResponse(List.of(), null);
        }

        // Collect all IDs and scores from hits
        List<UUID> allHitIds = hits.stream().map(PostSearchHit::getPostId).toList();
        Map<UUID, Double> scoreMap = hits.stream()
            .collect(Collectors.toMap(PostSearchHit::getPostId, PostSearchHit::getScore,
                (a, b) -> a, LinkedHashMap::new));

        // Fetch full Post entities by IDs
        List<Post> posts = postRepository.findAllById(allHitIds);

        // Re-sort to preserve search ranking order
        Map<UUID, Post> postMap = posts.stream()
            .collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> ordered = allHitIds.stream()
            .map(postMap::get)
            .filter(p -> p != null)
            .toList();

        if (!ordered.isEmpty()) {
            Set<UUID> distinctAuthors = ordered.stream()
                .map(Post::getAuthorId).collect(Collectors.toSet());
            Set<UUID> blocked = moderationBlocklistService.getBlockedFrom(distinctAuthors);
            if (!blocked.isEmpty()) {
                ordered = ordered.stream()
                    .filter(p -> !blocked.contains(p.getAuthorId()))
                    .toList();
            }
        }

        boolean hasMore = ordered.size() > effectiveSize;
        if (hasMore) ordered = ordered.subList(0, effectiveSize);

        if (ordered.isEmpty()) {
            return new FeedPageResponse(List.of(), null);
        }

        // Enrich identity
        Set<UUID> authorIdsSet = ordered.stream().map(Post::getAuthorId).collect(Collectors.toSet());
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(authorIdsSet);

        // Merge counters
        Set<UUID> postIds = ordered.stream().map(Post::getId).collect(Collectors.toSet());
        Map<UUID, CounterDeltas> deltas = counterService.getDeltasBatch(postIds);

        List<PostResponse> items = ordered.stream()
            .map(p -> postMapper.toResponse(
                p,
                profiles.get(p.getAuthorId()),
                deltas.getOrDefault(p.getId(), CounterDeltas.zero())))
            .toList();

        // Build next cursor from the last item
        String nextCursor = null;
        if (hasMore) {
            Post last = ordered.get(ordered.size() - 1);
            Double lastScore = scoreMap.get(last.getId());
            nextCursor = SearchCursor.encode(
                lastScore != null ? lastScore : 0.0,
                last.getPublishedAt(),
                last.getId());
        }

        return new FeedPageResponse(items, nextCursor);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) return DEFAULT_PAGE_SIZE;
        if (requested < 1) throw new IllegalArgumentException("pageSize must be >= 1");
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private static List<String> extractKeywords(String q) {
        if (q == null || q.isBlank()) return List.of();
        return Arrays.stream(q.split("\\s+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .distinct()
            .toList();
    }
}
