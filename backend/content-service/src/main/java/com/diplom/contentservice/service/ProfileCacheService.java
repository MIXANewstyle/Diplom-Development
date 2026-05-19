package com.diplom.contentservice.service;

import com.diplom.contentservice.client.UserServiceClient;
import com.diplom.contentservice.dto.UserBatchResponse;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileCacheService {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "user:";
    private static final String KEY_SUFFIX = ":profile";

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserServiceClient userServiceClient;

    /**
     * Fetch profiles for the given ids. Cache hits served from Redis;
     * misses fetched in a single batch from user-service and cached.
     * Returns a Map<UUID, UserBatchResponse> for O(1) lookup by callers.
     * Missing users (deleted in user-service) are absent from the map.
     */
    public Map<UUID, UserBatchResponse> getProfiles(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();

        Set<UUID> distinct = new HashSet<>(ids);
        Map<UUID, UserBatchResponse> result = new HashMap<>();
        List<UUID> misses = new ArrayList<>();

        // 1. Try cache for each id.
        for (UUID id : distinct) {
            String key = KEY_PREFIX + id + KEY_SUFFIX;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof UserBatchResponse profile) {
                result.put(id, profile);
            } else {
                misses.add(id);
            }
        }

        // 2. Single batched fetch for misses.
        if (!misses.isEmpty()) {
            List<UserBatchResponse> fetched = userServiceClient.batchGetProfiles(misses);
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
}
