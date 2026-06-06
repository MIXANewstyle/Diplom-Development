package com.diplom.chatservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationBlocklistService {

    private static final String KEY = "chat:moderated_user_ids";

    private final StringRedisTemplate stringRedisTemplate;

    public void block(UUID userId) {
        stringRedisTemplate.opsForSet().add(KEY, userId.toString());
        log.info("Added {} to moderation blocklist", userId);
    }

    public void unblock(UUID userId) {
        stringRedisTemplate.opsForSet().remove(KEY, userId.toString());
        log.info("Removed {} from moderation blocklist", userId);
    }

    public boolean isBlocked(UUID userId) {
        Boolean result = stringRedisTemplate.opsForSet().isMember(KEY, userId.toString());
        return Boolean.TRUE.equals(result);
    }
    
    // TODO: rebuild-on-startup (Phase 4b-2)
}
