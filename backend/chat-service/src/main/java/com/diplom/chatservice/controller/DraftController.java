package com.diplom.chatservice.controller;

import com.diplom.chatservice.dto.ws.DraftBroadcast;
import com.diplom.chatservice.dto.ws.DraftDeleteRequest;
import com.diplom.chatservice.dto.ws.DraftUpsertRequest;
import com.diplom.chatservice.dto.ws.WsError;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.DraftService;
import com.diplom.chatservice.service.WsErrorSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Handles inbound STOMP messages for draft buffer operations:
 * <ul>
 *   <li>{@code /app/rooms/{roomId}/draft/upsert} — insert or update a bubble.</li>
 *   <li>{@code /app/rooms/{roomId}/draft/delete} — delete a bubble.</li>
 * </ul>
 *
 * <p>Both handlers enforce the common precondition: room is ACTIVE, phase is A_COMPOSING,
 * and the caller is the floor-holder. Violations are sent as WS errors to the caller's
 * user queue — never broadcast.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DraftController {

    private static final int STATUS_ACTIVE = 3;
    private static final String PHASE_A_COMPOSING = "A_COMPOSING";

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final DraftService draftService;
    private final WsErrorSender wsErrorSender;
    private final com.diplom.chatservice.service.RoomBroadcaster roomBroadcaster;

    @MessageMapping("/rooms/{roomId}/draft/upsert")
    public void handleDraftUpsert(
            @DestinationVariable UUID roomId,
            @Payload DraftUpsertRequest request,
            Principal principal
    ) {
        Object authPrincipal = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        String principalName = "unknown";
        if (authPrincipal instanceof CustomUserDetails user) {
            principalName = user.getUsername();
        } else if (authPrincipal instanceof com.diplom.chatservice.security.GuestPrincipal guest) {
            principalName = "guest-" + guest.getParticipantId();
        }

        // Look up participant
        RoomParticipant participant = com.diplom.chatservice.security.SecurityUtils.getParticipantOrNull(authPrincipal, roomId, roomParticipantRepository);
        if (participant == null) {
            wsErrorSender.send(principalName, WsError.error("You are not a participant of this room"));
            return;
        }

        // Common precondition check
        if (!checkDraftPrecondition(roomId, participant.getId(), principalName)) {
            return;
        }

        // Validate payload
        if (request.bubbleId() == null) {
            wsErrorSender.send(principalName, WsError.error("bubbleId is required"));
            return;
        }

        // Attempt upsert
        try {
            draftService.upsert(roomId, participant.getId(), request.bubbleId(), request.text());
        } catch (DraftService.DraftLimitException e) {
            wsErrorSender.send(principalName, WsError.limit(e.getMessage()));
            return;
        }

        // Broadcast to the room
        roomBroadcaster.broadcast(
                roomId,
                DraftBroadcast.upsert(participant.getId(), request.bubbleId(), request.text())
        );

        log.debug("Draft UPSERT: roomId={}, participantId={}, bubbleId={}",
                roomId, participant.getId(), request.bubbleId());
    }

    @MessageMapping("/rooms/{roomId}/draft/delete")
    public void handleDraftDelete(
            @DestinationVariable UUID roomId,
            @Payload DraftDeleteRequest request,
            Principal principal
    ) {
        Object authPrincipal = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        String principalName = "unknown";
        if (authPrincipal instanceof CustomUserDetails user) {
            principalName = user.getUsername();
        } else if (authPrincipal instanceof com.diplom.chatservice.security.GuestPrincipal guest) {
            principalName = "guest-" + guest.getParticipantId();
        }

        // Look up participant
        RoomParticipant participant = com.diplom.chatservice.security.SecurityUtils.getParticipantOrNull(authPrincipal, roomId, roomParticipantRepository);
        if (participant == null) {
            wsErrorSender.send(principalName, WsError.error("You are not a participant of this room"));
            return;
        }

        // Common precondition check
        if (!checkDraftPrecondition(roomId, participant.getId(), principalName)) {
            return;
        }

        // Validate payload
        if (request.bubbleId() == null) {
            wsErrorSender.send(principalName, WsError.error("bubbleId is required"));
            return;
        }

        // Delete
        boolean removed = draftService.delete(roomId, participant.getId(), request.bubbleId());
        if (!removed) {
            // Bubble not found — not an error per se, just a no-op; but still broadcast
            // to keep participants in sync (idempotent delete)
        }

        // Broadcast to the room
        roomBroadcaster.broadcast(
                roomId,
                DraftBroadcast.delete(participant.getId(), request.bubbleId())
        );

        log.debug("Draft DELETE: roomId={}, participantId={}, bubbleId={}",
                roomId, participant.getId(), request.bubbleId());
    }

    /**
     * Checks the common precondition for draft operations:
     * room is ACTIVE, phase is A_COMPOSING, and the caller is the floor-holder.
     *
     * @return true if the precondition is met; false if an error was sent to the caller
     */
    private boolean checkDraftPrecondition(UUID roomId, UUID participantId, String principalName) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            wsErrorSender.send(principalName, WsError.error("Room not found"));
            return false;
        }

        if (room.getStatusId() != STATUS_ACTIVE) {
            wsErrorSender.send(principalName, WsError.error("Room is not active"));
            return false;
        }

        if (!PHASE_A_COMPOSING.equals(room.getPhase())) {
            wsErrorSender.send(principalName,
                    WsError.error("Cannot draft: room is in " + room.getPhase() + " phase"));
            return false;
        }

        if (!participantId.equals(room.getCurrentFloorParticipantId())) {
            wsErrorSender.send(principalName,
                    WsError.error("It is not your turn to draft"));
            return false;
        }

        return true;
    }


}
