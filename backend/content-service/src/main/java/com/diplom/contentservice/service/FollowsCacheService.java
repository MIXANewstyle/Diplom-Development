package com.diplom.contentservice.service;

import com.diplom.contentservice.client.UserServiceClient;
import com.diplom.contentservice.dto.FollowedAuthorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowsCacheService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "user:";
    private static final String KEY_SUFFIX = ":follows";

    private static final TypeReference<List<UUID>> UUID_LIST_TYPE =
        new TypeReference<>() {};

    private final StringRedisTemplate stringRedisTemplate;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;

    public List<UUID> getFollowedAuthorIds(UUID userId) {
        String key = keyFor(userId);

        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, UUID_LIST_TYPE);
            } catch (JsonProcessingException ex) {
                log.warn("Corrupted follows cache for user {} — refetching", userId, ex);
                stringRedisTemplate.delete(key);
            }
        }

        List<FollowedAuthorResponse> follows = userServiceClient.getFollowedAuthors(userId);
        List<UUID> ids = follows.stream()
            .map(FollowedAuthorResponse::authorId)
            .toList();

        try {
            String json = objectMapper.writeValueAsString(ids);
            stringRedisTemplate.opsForValue().set(key, json, TTL);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize follows for caching user {}", userId, ex);
        }

        return ids;
    }

    public void invalidate(UUID userId) {
        String key = keyFor(userId);
        Boolean deleted = stringRedisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Invalidated follows cache for user {}", userId);
        }
    }

    private String keyFor(UUID userId) {
        return KEY_PREFIX + userId + KEY_SUFFIX;
    }
}
