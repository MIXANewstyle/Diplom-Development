package com.diplom.contentservice.service;

import com.diplom.contentservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

@Component
@RequiredArgsConstructor
@Slf4j
public class CounterFlusher {

    private final CounterService counterService;
    private final PostRepository postRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void flushUpvotes() {
        flushBySuffix(":upvotes:delta", counterService::getAndDeleteUpvotesDelta, postRepository::applyUpvotesDelta);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void flushComments() {
        flushBySuffix(":comments:delta", counterService::getAndDeleteCommentsDelta, postRepository::applyCommentsDelta);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void flushViews() {
        flushBySuffix(":views:delta", counterService::getAndDeleteViewsDelta, postRepository::applyViewsDelta);
    }

    private void flushBySuffix(
            String suffix,
            java.util.function.Function<UUID, Long> getAndDelete,
            BiFunction<UUID, Long, Integer> applyToDb
    ) {
        Set<String> keys = counterService.scanAllKeys(suffix);
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int flushedCount = 0;
        for (String key : keys) {
            UUID postId = extractPostId(key);
            if (postId == null) {
                continue;
            }

            long delta = getAndDelete.apply(postId);
            if (delta == 0) {
                continue;
            }

            int affected = applyToDb.apply(postId, delta);
            if (affected == 0) {
                log.warn("Flush dropped delta {} for missing post {} (key {})", delta, postId, key);
            } else {
                flushedCount++;
            }
        }
        if (flushedCount > 0) {
            log.info("Flushed counters for {} posts (suffix {})", flushedCount, suffix);
        }
    }

    private UUID extractPostId(String key) {
        String[] parts = key.split(":");
        if (parts.length < 4 || !"post".equals(parts[0])) {
            return null;
        }
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException ex) {
            log.warn("Malformed counter key: {}", key);
            return null;
        }
    }
}
