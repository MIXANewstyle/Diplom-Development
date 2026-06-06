package com.diplom.chatservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the Redis-backed presence set for a room ({@code room:{id}:presence}).
 *
 * <p>Each member of the set is a participant id (UUID string). The key carries a
 * refresh TTL as a crash safety-net: if the service instance dies without clean
 * disconnect handling, the key self-expires. Every mutation refreshes the TTL.
 *
 * <p>Single-instance MVP. Redis Pub/Sub fan-out for multi-instance is deferred to Phase 4.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final StringRedisTemplate stringRedisTemplate;

    /** Crash-safety TTL for the presence set key. */
    private static final Duration PRESENCE_TTL = Duration.ofMinutes(30);

    private static String presenceKey(UUID roomId) {
        return "room:" + roomId + ":presence";
    }

    /**
     * Add a participant to the room's presence set and refresh the key TTL.
     */
    public void addPresence(UUID roomId, UUID participantId) {
        String key = presenceKey(roomId);
        stringRedisTemplate.opsForSet().add(key, participantId.toString());
        stringRedisTemplate.expire(key, PRESENCE_TTL);
        log.debug("Presence added: roomId={}, participantId={}", roomId, participantId);
    }

    /**
     * Remove a participant from the room's presence set. Refreshes TTL if set is non-empty,
     * otherwise deletes the key.
     */
    public void removePresence(UUID roomId, UUID participantId) {
        String key = presenceKey(roomId);
        stringRedisTemplate.opsForSet().remove(key, participantId.toString());

        Long remaining = stringRedisTemplate.opsForSet().size(key);
        if (remaining != null && remaining > 0) {
            stringRedisTemplate.expire(key, PRESENCE_TTL);
        } else {
            stringRedisTemplate.delete(key);
        }
        log.debug("Presence removed: roomId={}, participantId={}", roomId, participantId);
    }

    /**
     * Return the current set of online participant ids for a room.
     */
    public Set<UUID> getOnlineParticipants(UUID roomId) {
        String key = presenceKey(roomId);
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }
}
