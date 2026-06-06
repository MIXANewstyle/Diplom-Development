package com.diplom.chatservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleCacheService {

    private static final String KEY_PREFIX = "chat:user_role:";

    private final StringRedisTemplate stringRedisTemplate;

    public void putRole(UUID userId, String role) {
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId.toString(), role);
    }

    public String getCachedRole(UUID userId) {
        return stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId.toString());
    }
}
