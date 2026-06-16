package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.ws.DraftBubble;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages the Redis-backed draft buffer for a participant in a room
 * ({@code room:{id}:draft:{participantId}}).
 *
 * <p>The buffer is an ordered list of {@link DraftBubble} records, stored as a JSON array
 * in a Redis String. Read-modify-write per operation — safe for ≤30 small bubbles (§6.9).
 * The key is ephemeral / session-scoped (TTL), cleared on disconnect (and on FINISH_THOUGHT
 * in Phase 3c).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** Maximum number of draft bubbles per turn buffer (§6.9). */
    public static final int MAX_DRAFT_COUNT = 30;

    /** Maximum text length per draft bubble in characters (§6.9). */
    public static final int MAX_DRAFT_TEXT_LENGTH = 2000;

    /** Session-scoped TTL for draft buffer keys. */
    private static final Duration DRAFT_TTL = Duration.ofHours(2);

    private static final TypeReference<List<DraftBubble>> BUBBLE_LIST_TYPE =
            new TypeReference<>() {};

    static String draftKey(UUID roomId, UUID participantId) {
        return "room:" + roomId + ":draft:" + participantId;
    }

    /**
     * Upsert a bubble into the buffer: insert at end if unknown bubbleId, or replace in-place
     * if the bubbleId already exists (preserving position).
     *
     * @return the updated buffer (for callers that need it)
     * @throws DraftLimitException if text length exceeds 2000 or buffer would exceed 30 entries
     */
    public List<DraftBubble> upsert(UUID roomId, UUID participantId, UUID bubbleId, String text) {
        if (text != null && text.length() > MAX_DRAFT_TEXT_LENGTH) {
            throw new DraftLimitException(
                    "Draft text exceeds maximum length of " + MAX_DRAFT_TEXT_LENGTH + " characters");
        }

        List<DraftBubble> buffer = readBuffer(roomId, participantId);

        boolean found = false;
        for (int i = 0; i < buffer.size(); i++) {
            if (buffer.get(i).bubbleId().equals(bubbleId)) {
                buffer.set(i, new DraftBubble(bubbleId, text));
                found = true;
                break;
            }
        }

        if (!found) {
            if (buffer.size() >= MAX_DRAFT_COUNT) {
                throw new DraftLimitException(
                        "Draft buffer exceeds maximum of " + MAX_DRAFT_COUNT + " bubbles");
            }
            buffer.add(new DraftBubble(bubbleId, text));
        }

        writeBuffer(roomId, participantId, buffer);
        return buffer;
    }

    /**
     * Remove a bubble from the buffer by its id. No-op if not found.
     *
     * @return true if a bubble was removed
     */
    public boolean delete(UUID roomId, UUID participantId, UUID bubbleId) {
        List<DraftBubble> buffer = readBuffer(roomId, participantId);
        boolean removed = buffer.removeIf(b -> b.bubbleId().equals(bubbleId));
        if (removed) {
            writeBuffer(roomId, participantId, buffer);
        }
        return removed;
    }

    /**
     * Clear the entire draft buffer for a participant in a room (used on disconnect
     * and on FINISH_THOUGHT in Phase 3c).
     */
    public void clearBuffer(UUID roomId, UUID participantId) {
        String key = draftKey(roomId, participantId);
        stringRedisTemplate.delete(key);
        log.debug("Draft buffer cleared: roomId={}, participantId={}", roomId, participantId);
    }

    /**
     * Read the current buffer from Redis.
     */
    public List<DraftBubble> readBuffer(UUID roomId, UUID participantId) {
        String key = draftKey(roomId, participantId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, BUBBLE_LIST_TYPE));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse draft buffer for key={}, resetting", key, e);
            stringRedisTemplate.delete(key);
            return new ArrayList<>();
        }
    }

    private void writeBuffer(UUID roomId, UUID participantId, List<DraftBubble> buffer) {
        String key = draftKey(roomId, participantId);
        try {
            String json = objectMapper.writeValueAsString(buffer);
            stringRedisTemplate.opsForValue().set(key, json, DRAFT_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize draft buffer for key={}", key, e);
            throw new RuntimeException("Failed to serialize draft buffer", e);
        }
    }

    /**
     * Thrown when a draft operation would exceed configured limits (§6.9).
     * Caught by the controller and surfaced as a WS LIMIT error.
     */
    public static class DraftLimitException extends RuntimeException {
        public DraftLimitException(String message) {
            super(message);
        }
    }
}
