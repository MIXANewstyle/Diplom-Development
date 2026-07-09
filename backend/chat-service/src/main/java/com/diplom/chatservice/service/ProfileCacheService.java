package com.diplom.chatservice.service;

import com.diplom.chatservice.client.InternalUserBatchClient;
import com.diplom.chatservice.dto.UserBatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cache-aside service for user profiles.
 * Mirrors content-service's ProfileCacheService (§6.1 pattern).
 * Cache key: {@code user:{id}:profile}, TTL 60 s.
 * Does NOT consume PROFILE_CHANGED events — the 60 s TTL is the staleness bound.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileCacheService {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "user:";
    private static final String KEY_SUFFIX = ":profile";

    private final RedisTemplate<String, Object> redisTemplate;
    private final InternalUserBatchClient internalUserBatchClient;

    /**
     * Fetch profiles for the given ids. Cache hits served from Redis;
     * misses fetched in a single batch from user-service and cached.
     * Returns a Map&lt;UUID, UserBatchResponse&gt; for O(1) lookup by callers.
     * Missing users (deleted in user-service) are absent from the map.
     *
     * @param ids the user IDs to look up
     */
    public Map<UUID, UserBatchResponse> getProfiles(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();

        Set<UUID> distinct = new HashSet<>(ids);
        Map<UUID, UserBatchResponse> result = new HashMap<>();
        List<UUID> misses = new ArrayList<>();

        // 1. Try cache for each id.
        for (UUID id : distinct) {
            String key = KEY_PREFIX + id + KEY_SUFFIX;
            UserBatchResponse profile = readFromCache(key);
            if (profile != null) {
                result.put(id, profile);
            } else {
                misses.add(id);
            }
        }

        // 2. Single batched fetch for misses.
        if (!misses.isEmpty()) {
            List<UserBatchResponse> fetched = internalUserBatchClient.batchGetProfiles(misses);
            for (UserBatchResponse profile : fetched) {
                String key = KEY_PREFIX + profile.id() + KEY_SUFFIX;
                redisTemplate.opsForValue().set(key, profile, TTL);
                result.put(profile.id(), profile);
            }
            // Missing ids (not returned by user-service) are intentionally
            // absent from the result map. Callers must handle this case.
        }

        return result;
    }

    /**
     * Reads a cached profile. Entries written by other services (e.g. content-service)
     * use a different {@code @class} and may deserialize as {@link Map} or fail
     * deserialization entirely — both cases are treated as cache misses.
     */
    private UserBatchResponse readFromCache(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            return toUserBatchResponse(cached);
        } catch (Exception ex) {
            log.warn("Failed to read profile cache key {}: {}", key, ex.getMessage());
            evictCacheKey(key);
            return null;
        }
    }

    private void evictCacheKey(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.debug("Failed to evict profile cache key {}: {}", key, ex.getMessage());
        }
    }

    private UserBatchResponse toUserBatchResponse(Object cached) {
        if (cached == null) {
            return null;
        }
        if (cached instanceof UserBatchResponse profile) {
            return profile;
        }
        if (cached instanceof Map<?, ?> map) {
            Object idValue = map.get("id");
            Object username = map.get("username");
            if (idValue == null || username == null) {
                return null;
            }
            UUID id = idValue instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(idValue));
            Object avatar = map.get("avatarUrl");
            String avatarUrl = avatar == null ? null : String.valueOf(avatar);
            Object fullName = map.get("fullName");
            String fullNameStr = fullName == null ? null : String.valueOf(fullName);
            return new UserBatchResponse(id, String.valueOf(username), fullNameStr, avatarUrl);
        }
        return null;
    }
}
