package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLimitsProperties;
import com.diplom.chatservice.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final RoomRepository roomRepository;
    private final ChatLimitsProperties limits;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Checks if the participant has exceeded the turns-per-minute limit.
     * Increments the counter and sets a 60s TTL if it's new.
     * Returns true if OVER limit.
     */
    public boolean checkTurnRate(UUID participantId) {
        String key = "chat:rl:turns:" + participantId;
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        
        Long count = ops.increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        
        return count != null && count > limits.turnsPerMinute();
    }

    /**
     * Checks if the user has reached their daily token budget.
     * Evaluates before allowing LLM generation.
     * Returns true if OVER or EQUAL to budget.
     */
    public boolean isOverDailyBudget(UUID userId) {
        String key = getDailyTokenKey(userId);
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        
        String val = ops.get(key);
        if (val == null) {
            return false;
        }
        
        try {
            long tokens = Long.parseLong(val);
            return tokens >= limits.dailyTokenBudget();
        } catch (NumberFormatException e) {
            return false; // Safely ignore malformed cache values
        }
    }

    /**
     * Adds prompt + completion tokens to the user's daily budget usage.
     * Executed safely after LLM requests complete.
     */
    public void addDailyTokens(UUID userId, int tokens) {
        if (tokens <= 0 || userId == null) return;
        
        String key = getDailyTokenKey(userId);
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        
        try {
            Long newValue = ops.increment(key, tokens);
            // If it's a new key, expire it after ~48h to prevent unbounded Redis growth
            if (newValue != null && newValue == tokens) {
                redisTemplate.expire(key, 48, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("Failed to increment daily tokens for user {}", userId, e);
        }
    }

    /**
     * Counts active (non-terminal) rooms for a user.
     */
    public int countActiveRooms(UUID userId) {
        return roomRepository.countActiveOrEndingRoomsByParticipantUserId(userId);
    }

    private String getDailyTokenKey(UUID userId) {
        String dateStr = ZonedDateTime.now(ZoneOffset.UTC).format(DATE_FORMATTER);
        return "chat:rl:tokens:" + userId + ":" + dateStr;
    }
}
