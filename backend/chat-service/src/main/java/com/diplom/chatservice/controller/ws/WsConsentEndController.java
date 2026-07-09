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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WsConsentEndController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomParticipantRepository participantRepository;
    private final ContextSnapshotService contextSnapshotService;
    private final com.diplom.chatservice.service.RoomBroadcaster roomBroadcaster;

    private void sendError(String username, String message) {
        messagingTemplate.convertAndSendToUser(username, "/queue/errors", WsError.error(message));
    }

    private ParticipantResponse findParticipantResponse(RoomResponse response, UUID participantId) {
        return response.participants().stream()
                .filter(p -> p.id().equals(participantId))
                .findFirst()
                .orElse(null);
    }

    private Object extractAuthPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }
        return ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
    }

    @MessageMapping("/rooms/{roomId}/consent/start")
    public void consentStart(
            @DestinationVariable UUID roomId,
            Principal principal
    ) {
        Object authPrincipal = extractAuthPrincipal(principal);
        String principalName = getPrincipalName(authPrincipal);
        org.slf4j.MDC.put("roomId", roomId.toString());
        try {
            RoomParticipant caller = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(authPrincipal, roomId, participantRepository);
            RoomResponse response = roomService.consentStart(roomId, authPrincipal);

            if ("ACTIVE".equals(response.status())) {
                roomBroadcaster.broadcast(roomId,
                        DialogueStartedEvent.of(response.currentFloorParticipantId()));
                contextSnapshotService.captureForRoom(roomId);
            } else {
                ParticipantResponse p = findParticipantResponse(response, caller.getId());
                if (p != null) {
                    roomBroadcaster.broadcast(roomId,
                            ConsentUpdatedEvent.of(p.id(), p.consentStartAt()));
                }
            }
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in consentStart for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS consentStart", e);
            sendError(principalName, "An unexpected error occurred");
        } finally {
            org.slf4j.MDC.remove("roomId");
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

    @MessageMapping("/rooms/{roomId}/consent/revoke")
    public void consentRevoke(
            @DestinationVariable UUID roomId,
            Principal principal
    ) {
        Object authPrincipal = extractAuthPrincipal(principal);
        String principalName = getPrincipalName(authPrincipal);
        org.slf4j.MDC.put("roomId", roomId.toString());
        try {
            RoomParticipant caller = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(authPrincipal, roomId, participantRepository);
            RoomResponse response = roomService.consentRevoke(roomId, authPrincipal);

            ParticipantResponse pResponse = findParticipantResponse(response, caller.getId());
            if (pResponse != null) {
                roomBroadcaster.broadcast(roomId,
                        ConsentUpdatedEvent.of(pResponse.id(), pResponse.consentStartAt()));
            }
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in consentRevoke for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS consentRevoke", e);
            sendError(principalName, "An unexpected error occurred");
        } finally {
            org.slf4j.MDC.remove("roomId");
        }
    }

    @MessageMapping("/rooms/{roomId}/end/propose")
    public void endPropose(
            @DestinationVariable UUID roomId,
            Principal principal
    ) {
        Object authPrincipal = extractAuthPrincipal(principal);
        String principalName = getPrincipalName(authPrincipal);
        org.slf4j.MDC.put("roomId", roomId.toString());
        try {
            RoomParticipant caller = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(authPrincipal, roomId, participantRepository);
            RoomResponse response = roomService.endPropose(roomId, authPrincipal);

            ParticipantResponse pResponse = findParticipantResponse(response, caller.getId());
            if (pResponse != null) {
                roomBroadcaster.broadcast(roomId,
                        EndProposedEvent.of(pResponse.id()));
            }
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in endPropose for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS endPropose", e);
            sendError(principalName, "An unexpected error occurred");
        } finally {
            org.slf4j.MDC.remove("roomId");
        }
    }

    @MessageMapping("/rooms/{roomId}/end/agree")
    public void endAgree(
            @DestinationVariable UUID roomId,
            Principal principal
    ) {
        Object authPrincipal = extractAuthPrincipal(principal);
        String principalName = getPrincipalName(authPrincipal);
        org.slf4j.MDC.put("roomId", roomId.toString());
        try {
            com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(authPrincipal, roomId, participantRepository);
            roomService.endRespond(roomId, new EndRespondRequest(EndDecision.AGREE), authPrincipal);

            roomBroadcaster.broadcast(roomId,
                    DialogueArchivedEvent.of(roomId, OffsetDateTime.now()));
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in endAgree for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS endAgree", e);
            sendError(principalName, "An unexpected error occurred");
        } finally {
            org.slf4j.MDC.remove("roomId");
        }
    }

    @MessageMapping("/rooms/{roomId}/end/decline")
    public void endDecline(
            @DestinationVariable UUID roomId,
            Principal principal
    ) {
        Object authPrincipal = extractAuthPrincipal(principal);
        String principalName = getPrincipalName(authPrincipal);
        org.slf4j.MDC.put("roomId", roomId.toString());
        try {
            com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(authPrincipal, roomId, participantRepository);
            RoomResponse response = roomService.endRespond(roomId, new EndRespondRequest(EndDecision.DECLINE), authPrincipal);

            roomBroadcaster.broadcast(roomId,
                    EndDeclinedEvent.of(response.currentFloorParticipantId()));
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in endDecline for room {}: {}", roomId, e.getMessage());
            sendError(principalName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in WS endDecline", e);
            sendError(principalName, "An unexpected error occurred");
        } finally {
            org.slf4j.MDC.remove("roomId");
        }
    }
}
