package com.diplom.chatservice.security;

import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

public class SecurityUtils {

    public static boolean isAuthorizedForRoom(Object principal, UUID roomId, RoomParticipantRepository repository) {
        if (principal instanceof GuestPrincipal guest) {
            return guest.getRoomId().equals(roomId) && repository.existsByIdAndRoomId(guest.getParticipantId(), roomId);
        } else if (principal instanceof CustomUserDetails user) {
            return repository.existsByRoomIdAndUserId(roomId, user.getId());
        }
        return false;
    }

    public static RoomParticipant getParticipantOrNull(Object principal, UUID roomId, RoomParticipantRepository repository) {
        if (principal instanceof GuestPrincipal guest) {
            if (!guest.getRoomId().equals(roomId)) {
                return null;
            }
            return repository.findById(guest.getParticipantId())
                    .filter(p -> p.getRoomId().equals(roomId))
                    .orElse(null);
        } else if (principal instanceof CustomUserDetails user) {
            return repository.findByRoomIdAndUserId(roomId, user.getId())
                    .orElse(null);
        }
        return null;
    }

    public static RoomParticipant getParticipantOrThrow(Object principal, UUID roomId, RoomParticipantRepository repository) {
        RoomParticipant participant = getParticipantOrNull(principal, roomId, repository);
        if (participant == null) {
            throw new AccessDeniedException("Access denied: you are not a participant of this room");
        }
        return participant;
    }
    
    public static UUID getUserIdOrNull(Object principal) {
        if (principal instanceof CustomUserDetails user) {
            return user.getId();
        }
        return null;
    }
}
