package com.diplom.contentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationBlocklistService {

    private static final String KEY = "content:moderated_author_ids";

    private final StringRedisTemplate stringRedisTemplate;

    public void addToBlocklist(UUID userId) {
        stringRedisTemplate.opsForSet().add(KEY, userId.toString());
        log.info("Added {} to moderation blocklist", userId);
    }

    public void removeFromBlocklist(UUID userId) {
        stringRedisTemplate.opsForSet().remove(KEY, userId.toString());
        log.info("Removed {} from moderation blocklist", userId);
    }

    public boolean isBlocked(UUID userId) {
        Boolean result = stringRedisTemplate.opsForSet().isMember(KEY, userId.toString());
        return Boolean.TRUE.equals(result);
    }

    /**
     * Batch check via SMISMEMBER. Returns the subset of input ids
     * that are currently in the blocklist. Empty input -> empty set.
     */
    public Set<UUID> getBlockedFrom(Collection<UUID> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) return Set.of();
        List<UUID> distinct = authorIds.stream().distinct().toList();
        Object[] idStrings = distinct.stream().map(UUID::toString).toArray();

        Map<Object, Boolean> results = stringRedisTemplate.opsForSet()
            .isMember(KEY, idStrings);

        if (results == null) return Set.of();

        Set<UUID> blocked = new HashSet<>();
        for (UUID id : distinct) {
            if (Boolean.TRUE.equals(results.get(id.toString()))) {
                blocked.add(id);
            }
        }
        return blocked;
    }
}
