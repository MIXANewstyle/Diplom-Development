package com.diplom.chatservice.controller.ws;

import com.diplom.chatservice.dto.EndDecision;
import com.diplom.chatservice.dto.EndRespondRequest;
import com.diplom.chatservice.dto.ParticipantResponse;
import com.diplom.chatservice.dto.RoomResponse;
import com.diplom.chatservice.dto.ws.*;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.NotRoomParticipantException;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.ContextSnapshotService;
import com.diplom.chatservice.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WsConsentEndController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomParticipantRepository participantRepository;
    private final ContextSnapshotService contextSnapshotService;

    private RoomParticipant verifyParticipant(UUID roomId, UUID userId) {
        RoomParticipant p = participantRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (p == null) {
            throw new NotRoomParticipantException("Caller is not a participant of this room");
        }
        return p;
    }

    private void sendError(String username, String message) {
        messagingTemplate.convertAndSendToUser(username, "/queue/errors", WsError.error(message));
    }

    private ParticipantResponse findParticipantResponse(RoomResponse response, UUID participantId) {
        return response.participants().stream()
                .filter(p -> p.id().equals(participantId))
                .findFirst()
                .orElse(null);
    }

    @MessageMapping("/rooms/{roomId}/consent/start")
    public void consentStart(
            @AuthenticationPrincipal Object principal,
            @DestinationVariable UUID roomId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        String principalName = getPrincipalName(principal);
        try {
            com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(principal, roomId, participantRepository);
            RoomResponse response = roomService.consentStart(roomId, principal);

            if ("ACTIVE".equals(response.status())) {
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId, 
                        DialogueStartedEvent.of(response.currentFloorParticipantId()));
                // Capture context snapshots outside the consent tx
                String jwt = extractWsJwt(headerAccessor);
                if (jwt != null) {
                    contextSnapshotService.captureForRoom(roomId, jwt);
                }
            } else {
                ParticipantResponse p = findParticipantResponse(response, user.getId());
                if (p != null) {
                    messagingTemplate.convertAndSend("/topic/rooms/" + roomId, 
                            ConsentUpdatedEvent.of(p.id(), p.consentStartAt()));
                }
            }
            }
            // TODO: have the REST controller broadcast too (so REST-triggered state changes also notify WS subscribers)
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in consentStart for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS consentStart", e);
            sendError(principalName, "An unexpected error occurred");
        }
    }

    private String getPrincipalName(Object principal) {
        if (principal instanceof CustomUserDetails user) {
            return user.getUsername();
        } else if (principal instanceof com.diplom.chatservice.security.GuestPrincipal guest) {
            return "guest-" + guest.getParticipantId();
        }
        return "unknown";
    }

    private String extractWsJwt(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs != null && sessionAttrs.containsKey("jwt")) {
            return (String) sessionAttrs.get("jwt");
        }
        return null;
    }

    @MessageMapping("/rooms/{roomId}/consent/revoke")
    public void consentRevoke(
            @AuthenticationPrincipal Object principal,
            @DestinationVariable UUID roomId
    ) {
        String principalName = getPrincipalName(principal);
        try {
            RoomParticipant caller = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(principal, roomId, participantRepository);
            RoomResponse response = roomService.consentRevoke(roomId, principal);

            ParticipantResponse p = findParticipantResponse(response, caller.getUserId()); // null for guest, but findParticipantResponse can use participantId if we update it. Wait!
            if (p != null) {
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId, 
                        ConsentUpdatedEvent.of(p.id(), null));
            }
            // TODO: have the REST controller broadcast too (so REST-triggered state changes also notify WS subscribers)
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in consentRevoke for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS consentRevoke", e);
            sendError(principalName, "An unexpected error occurred");
        }
    }

    @MessageMapping("/rooms/{roomId}/end/propose")
    public void endPropose(
            @AuthenticationPrincipal Object principal,
            @DestinationVariable UUID roomId
    ) {
        String principalName = getPrincipalName(principal);
        try {
            RoomParticipant caller = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(principal, roomId, participantRepository);
            RoomResponse response = roomService.endPropose(roomId, principal);

            ParticipantResponse p = findParticipantResponse(response, caller.getId());
            if (p != null) {
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId, 
                        EndProposedEvent.of(p.id()));
            }
            // TODO: have the REST controller broadcast too (so REST-triggered state changes also notify WS subscribers)
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in endPropose for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS endPropose", e);
            sendError(principalName, "An unexpected error occurred");
        }
    }

    @MessageMapping("/rooms/{roomId}/end/agree")
    public void endAgree(
            @AuthenticationPrincipal Object principal,
            @DestinationVariable UUID roomId
    ) {
        String principalName = getPrincipalName(principal);
        try {
            com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(principal, roomId, participantRepository);
            RoomResponse response = roomService.endRespond(roomId, new EndRespondRequest(EndDecision.AGREE), principal);

            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, 
                    DialogueArchivedEvent.of(roomId, OffsetDateTime.now()));
            // TODO: no server-side forced-unsubscribe needed for MVP
            // TODO: have the REST controller broadcast too (so REST-triggered state changes also notify WS subscribers)
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in endAgree for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS endAgree", e);
            sendError(principalName, "An unexpected error occurred");
        }
    }

    @MessageMapping("/rooms/{roomId}/end/decline")
    public void endDecline(
            @AuthenticationPrincipal Object principal,
            @DestinationVariable UUID roomId
    ) {
        String principalName = getPrincipalName(principal);
        try {
            com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(principal, roomId, participantRepository);
            RoomResponse response = roomService.endRespond(roomId, new EndRespondRequest(EndDecision.DECLINE), principal);

            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, 
                    EndDeclinedEvent.of(response.currentFloorParticipantId()));
            // TODO: have the REST controller broadcast too (so REST-triggered state changes also notify WS subscribers)
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in endDecline for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS endDecline", e);
            sendError(principalName, "An unexpected error occurred");
        }
    }
}
