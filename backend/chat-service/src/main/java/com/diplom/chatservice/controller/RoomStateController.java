package com.diplom.chatservice.controller;

import com.diplom.chatservice.dto.ParticipantResponse;
import com.diplom.chatservice.dto.RoomStateSnapshot;
import com.diplom.chatservice.dto.TurnResponse;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.ParticipantEnrichmentService;
import com.diplom.chatservice.service.PresenceService;
import com.diplom.chatservice.service.RoomMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles STOMP subscribe-time room state snapshot.
 *
 * <p>The {@code @SubscribeMapping} return value is sent only to the subscribing client
 * (not broadcast). The {@link com.diplom.chatservice.security.AuthChannelInterceptor}
 * has already verified the subscriber is a room participant before this handler runs.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomStateController {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final TurnRepository turnRepository;
    private final RoomMapper roomMapper;
    private final ParticipantEnrichmentService participantEnrichmentService;
    private final PresenceService presenceService;

    /**
     * Returns a one-shot {@link RoomStateSnapshot} to the subscribing client.
     * Called when a client subscribes to {@code /app/rooms/{roomId}/state}.
     *
     * @param roomId        the room UUID from the destination path
     * @param headerAccessor STOMP header accessor (provides session attributes + principal)
     * @return room state snapshot with enriched participants and last 50 turns
     */
    @SubscribeMapping("/rooms/{roomId}/state")
    public RoomStateSnapshot onSubscribeRoomState(
            @DestinationVariable UUID roomId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) headerAccessor.getUser();
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

        log.debug("Room state snapshot requested by userId={} for roomId={}",
                userDetails.getId(), roomId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        List<RoomParticipant> participants = roomParticipantRepository.findByRoomId(roomId);

        // Retrieve the raw JWT stored during STOMP CONNECT
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        String jwt = sessionAttributes != null ? (String) sessionAttributes.get("jwt") : null;

        List<ParticipantResponse> participantResponses;
        if (jwt != null) {
            participantResponses = participantEnrichmentService.enrichParticipants(participants, jwt);
        } else {
            log.warn("No JWT in STOMP session attributes for userId={}, skipping enrichment",
                    userDetails.getId());
            participantResponses = participants.stream()
                    .map(roomMapper::toParticipantResponse)
                    .toList();
        }

        // Last 50 turns by seq ascending (fetch desc, then reverse)
        List<Turn> turns = turnRepository.findTop50ByRoomIdOrderBySeqDesc(roomId);
        List<TurnResponse> turnResponses = turns.reversed().stream()
                .map(roomMapper::toTurnResponse)
                .toList();

        return new RoomStateSnapshot(
                room.getId(),
                roomMapper.roomTypeName(room.getTypeId()),
                roomMapper.roomStatusName(room.getStatusId()),
                room.getPhase(),
                room.getCurrentFloorParticipantId(),
                participantResponses,
                turnResponses,
                presenceService.getOnlineParticipants(roomId)
        );
    }
}

