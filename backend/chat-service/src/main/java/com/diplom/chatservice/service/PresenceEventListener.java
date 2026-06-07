package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.ws.PresenceSnapshot;
import com.diplom.chatservice.dto.ws.PresenceUpdated;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens to STOMP session lifecycle events to track presence:
 * <ul>
 *   <li>{@link SessionSubscribeEvent} to {@code /topic/rooms/{roomId}} — mark participant online.</li>
 *   <li>{@link SessionDisconnectEvent} — mark participant offline for all rooms in that session.</li>
 * </ul>
 *
 * <p>Presence data is stored in Redis via {@link PresenceService}. An in-memory
 * {@code sessionRoomMap} maps STOMP session ids to room ids for efficient disconnect cleanup.
 *
 * <p><b>Single-instance MVP.</b> The in-memory map is local to this instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final DraftService draftService;
    private final RoomParticipantRepository roomParticipantRepository;
    private final SimpMessagingTemplate messagingTemplate; // Kept for targeted queue messages
    private final WsSessionRegistry wsSessionRegistry;
    private final RoomBroadcaster roomBroadcaster;

    /**
     * Maps STOMP sessionId → set of {roomId, participantId} pairs the session subscribed to.
     * // TODO: move to Redis for multi-instance in Phase 4
     */
    private final Map<String, Set<SessionRoomEntry>> sessionRoomMap = new ConcurrentHashMap<>();

    /** Matches only {@code /topic/rooms/{roomId}} (the room channel used for presence). */
    private static final Pattern TOPIC_ROOM_PATTERN =
            Pattern.compile("^/topic/rooms/([0-9a-fA-F\\-]{36})$");

    /**
     * On subscribe to {@code /topic/rooms/{roomId}}:
     * <ol>
     *   <li>Resolve the principal's participant id in that room.</li>
     *   <li>Add to Redis presence set.</li>
     *   <li>Register sessionId → roomId in the in-memory map.</li>
     *   <li>Broadcast PRESENCE_UPDATED ONLINE to the room.</li>
     *   <li>Send the current presence snapshot to the newcomer only.</li>
     * </ol>
     */
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();

        if (destination == null) {
            return;
        }

        Matcher matcher = TOPIC_ROOM_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            // Not the room topic channel — ignore (e.g., /app/rooms/{id}/state subscription)
            return;
        }

        UUID roomId;
        try {
            roomId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid roomId in subscribe destination: {}", destination);
            return;
        }

        CustomUserDetails userDetails = extractUserDetails(accessor);
        if (userDetails == null) {
            log.warn("No authenticated principal on SessionSubscribeEvent for destination={}", destination);
            return;
        }

        UUID userId = userDetails.getId();
        RoomParticipant participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElse(null);
        if (participant == null) {
            log.warn("User {} subscribed to room {} but is not a participant", userId, roomId);
            return;
        }

        UUID participantId = participant.getId();
        String sessionId = accessor.getSessionId();

        // 1. Add to Redis presence set
        presenceService.addPresence(roomId, participantId);

        // 2. Register in the in-memory session map
        sessionRoomMap
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(new SessionRoomEntry(roomId, participantId));

        // 3. Broadcast ONLINE to the room topic
        roomBroadcaster.broadcast(
                roomId,
                PresenceUpdated.online(participantId)
        );

        // 4. Send current presence snapshot to the newcomer only
        Set<UUID> onlineSet = presenceService.getOnlineParticipants(roomId);
        messagingTemplate.convertAndSendToUser(
                userDetails.getUsername(),        // principal name = email
                "/queue/presence",
                PresenceSnapshot.of(roomId, onlineSet)
        );

        log.debug("Presence ONLINE: sessionId={}, roomId={}, participantId={}",
                sessionId, roomId, participantId);
    }

    /**
     * On disconnect: for each roomId registered to that session, remove the participant
     * from the presence set, clear the draft buffer, and broadcast OFFLINE.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        wsSessionRegistry.unregisterSessionId(sessionId);

        Set<SessionRoomEntry> entries = sessionRoomMap.remove(sessionId);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (SessionRoomEntry entry : entries) {
            UUID roomId = entry.roomId();
            UUID participantId = entry.participantId();

            // Remove from Redis presence
            presenceService.removePresence(roomId, participantId);

            // Clear draft buffer (ephemeral, cleared on disconnect — §3.4)
            draftService.clearBuffer(roomId, participantId);

            // Broadcast OFFLINE
            roomBroadcaster.broadcast(
                    roomId,
                    PresenceUpdated.offline(participantId)
            );

            log.debug("Presence OFFLINE: sessionId={}, roomId={}, participantId={}",
                    sessionId, roomId, participantId);
        }
    }

    /**
     * Extract the {@link CustomUserDetails} from a STOMP message header.
     */
    private CustomUserDetails extractUserDetails(SimpMessageHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            return null;
        }
        try {
            var auth = (org.springframework.security.authentication.UsernamePasswordAuthenticationToken)
                    accessor.getUser();
            return (CustomUserDetails) auth.getPrincipal();
        } catch (ClassCastException e) {
            log.warn("Unexpected principal type on STOMP session", e);
            return null;
        }
    }

    /**
     * In-memory entry linking a room to the participant id for a given session.
     */
    private record SessionRoomEntry(UUID roomId, UUID participantId) {}
}
