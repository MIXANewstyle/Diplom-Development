package com.diplom.contentservice.feed;

import com.diplom.contentservice.dto.CounterDeltas;
import com.diplom.contentservice.dto.FeedPageResponse;
import com.diplom.contentservice.dto.PostResponse;
import com.diplom.contentservice.dto.UserBatchResponse;
import com.diplom.contentservice.entity.Post;
import com.diplom.contentservice.repository.PostRepository;
import com.diplom.contentservice.service.CounterService;
import com.diplom.contentservice.service.FollowsCacheService;
import com.diplom.contentservice.service.PostMapper;
import com.diplom.contentservice.service.ProfileCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final double OVERFETCH_FACTOR = 1.25;

    private final PostRepository postRepository;
    private final ProfileCacheService profileCacheService;
    private final CounterService counterService;
    private final PostMapper postMapper;
    private final FollowsCacheService followsCacheService;

    @Transactional(readOnly = true)
    public FeedPageResponse getFeed(
        SortMode sort,
        String cursor,
        Integer pageSize,
        List<UUID> tagIds,
        UUID currentUserId
    ) {
        int effectiveSize = clampPageSize(pageSize);
        int fetchLimit = (int) Math.ceil(effectiveSize * OVERFETCH_FACTOR) + 1;

        FeedCursor c = FeedCursor.decode(cursor);

        String[] tagIdArr;
        int tagCount;
        if (tagIds == null || tagIds.isEmpty()) {
            tagIdArr = new String[0];
            tagCount = 0;
        } else {
            tagIdArr = tagIds.stream().map(UUID::toString).toArray(String[]::new);
            tagCount = tagIds.size();
        }

        List<Post> fetched = switch (sort) {
            case NEWEST -> postRepository.feedNewest(
                c == null ? null : c.publishedAt(),
                c == null ? null : c.id(),
                tagIdArr, tagCount, fetchLimit);
            case MOST_LIKED -> postRepository.feedMostLiked(
                c == null ? null : c.sortValue(),
                c == null ? null : c.publishedAt(),
                c == null ? null : c.id(),
                tagIdArr, tagCount, fetchLimit);
            case MOST_COMMENTED -> postRepository.feedMostCommented(
                c == null ? null : c.sortValue(),
                c == null ? null : c.publishedAt(),
                c == null ? null : c.id(),
                tagIdArr, tagCount, fetchLimit);
            case FOLLOWING -> {
                List<UUID> followedAuthorIds = followsCacheService.getFollowedAuthorIds(currentUserId);
                if (followedAuthorIds.isEmpty()) {
                    yield List.of();
                }
                String[] authorIdArr = followedAuthorIds.stream()
                    .map(UUID::toString).toArray(String[]::new);
                yield postRepository.feedFollowing(
                    authorIdArr,
                    c == null ? null : c.publishedAt(),
                    c == null ? null : c.id(),
                    tagIdArr, tagCount, fetchLimit);
            }
        };

        // TODO Phase 7: apply moderation blocklist filtering here.
        // For now, the overfetched results pass through unfiltered.
        List<Post> filtered = fetched;

        boolean hasMore = filtered.size() > effectiveSize;
        if (hasMore) filtered = filtered.subList(0, effectiveSize);

        if (filtered.isEmpty()) {
            return new FeedPageResponse(List.of(), null);
        }

        Set<UUID> authorIds = filtered.stream().map(Post::getAuthorId).collect(Collectors.toSet());
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(authorIds);

        Set<UUID> postIds = filtered.stream().map(Post::getId).collect(Collectors.toSet());
        Map<UUID, CounterDeltas> deltas = counterService.getDeltasBatch(postIds);

        List<PostResponse> items = filtered.stream()
            .map(p -> postMapper.toResponse(
                p,
                profiles.get(p.getAuthorId()),
                deltas.getOrDefault(p.getId(), CounterDeltas.zero())))
            .toList();

        String nextCursor = null;
        if (hasMore) {
            Post last = filtered.get(filtered.size() - 1);
            Long sortValue = switch (sort) {
                case NEWEST, FOLLOWING -> null;
                case MOST_LIKED -> last.getUpvotesCount().longValue();
                case MOST_COMMENTED -> last.getCommentsCount().longValue();
            };
            nextCursor = FeedCursor.encode(sortValue, last.getPublishedAt(), last.getId());
        }

        return new FeedPageResponse(items, nextCursor);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) return DEFAULT_PAGE_SIZE;
        if (requested < 1) throw new IllegalArgumentException("pageSize must be >= 1");
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}
