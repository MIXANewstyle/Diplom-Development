package com.diplom.chatservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomBroadcaster {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Serializes the payload to JSON and publishes it to Redis topic "room.events.{roomId}".
     * The single entry point for all room topic broadcasts.
     */
    public void broadcast(UUID roomId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String topic = "room.events." + roomId;
            stringRedisTemplate.convertAndSend(topic, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize broadcast payload for room {}: {}", roomId, e.getMessage());
        }
    }
}
