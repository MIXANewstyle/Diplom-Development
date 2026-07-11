package com.diplom.chatservice.controller.ws;

import com.diplom.chatservice.dto.ws.PresenceCommand;
import com.diplom.chatservice.dto.ws.PresenceUpdated;
import com.diplom.chatservice.dto.ws.WsError;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.NotRoomParticipantException;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.PresenceService;
import com.diplom.chatservice.service.RoomBroadcaster;
import com.diplom.chatservice.service.WsErrorSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PresenceWsController {

    private final PresenceService presenceService;
    private final RoomParticipantRepository participantRepository;
    private final RoomBroadcaster roomBroadcaster;
    private final WsErrorSender wsErrorSender;

    private Object extractAuthPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }
        return ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
    }

    private String getPrincipalName(Object principal) {
        if (principal instanceof CustomUserDetails user) {
            return user.getUsername();
        } else if (principal instanceof com.diplom.chatservice.security.GuestPrincipal guest) {
            return guest.getParticipantId().toString();
        }
        return "unknown";
    }

    @Transactional
    @MessageMapping("/rooms/{roomId}/presence")
    public void handlePresence(
            @DestinationVariable UUID roomId,
            PresenceCommand cmd,
            Principal principal
    ) {
        Object authPrincipal = extractAuthPrincipal(principal);
        String principalName = getPrincipalName(authPrincipal);
        org.slf4j.MDC.put("roomId", roomId.toString());
        try {
            RoomParticipant caller = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(
                    authPrincipal, roomId, participantRepository);
            UUID participantId = caller.getId();

            if (cmd != null && cmd.away()) {
                presenceService.removePresence(roomId, participantId);
                roomBroadcaster.broadcast(roomId, PresenceUpdated.offline(participantId));
                log.info("Presence AWAY: roomId={} participantId={}", roomId, participantId);
            } else {
                presenceService.addPresence(roomId, participantId);
                roomBroadcaster.broadcast(roomId, PresenceUpdated.online(participantId));
                participantRepository.updateLastSeenAt(roomId, participantId, OffsetDateTime.now());
                log.info("Presence BACK: roomId={} participantId={}", roomId, participantId);
            }
        } catch (org.springframework.security.access.AccessDeniedException | RoomNotFoundException | NotRoomParticipantException | InvalidRoomStateException | IllegalArgumentException e) {
            log.warn("WS error in handlePresence for room {}: {}", roomId, e.getMessage());
            wsErrorSender.send(principalName, WsError.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in WS handlePresence", e);
            wsErrorSender.send(principalName, WsError.error("An unexpected error occurred"));
        } finally {
            org.slf4j.MDC.remove("roomId");
        }
    }
}
