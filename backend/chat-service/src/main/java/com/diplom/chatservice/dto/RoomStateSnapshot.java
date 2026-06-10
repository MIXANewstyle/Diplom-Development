package com.diplom.chatservice.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One-shot snapshot of room state, returned to the subscribing client on
 * {@code @SubscribeMapping("/rooms/{roomId}/state")}. Fields mirror what
 * {@code GET /api/v1/rooms/{roomId}} returns plus the last 50 turns.
 */
public record RoomStateSnapshot(
    UUID roomId,
    String type,
    String status,
    String phase,
    UUID currentFloorParticipantId,
    List<ParticipantResponse> participants,
    List<TurnResponse> recentTurns,
    Set<UUID> onlineParticipantIds
) {}
