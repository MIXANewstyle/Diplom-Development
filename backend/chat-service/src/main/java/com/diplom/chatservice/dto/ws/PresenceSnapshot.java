package com.diplom.chatservice.dto.ws;

import java.util.Set;
import java.util.UUID;

/**
 * Targeted message sent to a newcomer on {@code /user/queue/presence}
 * containing the set of currently online participant ids for the room.
 */
public record PresenceSnapshot(
    String type,
    UUID roomId,
    Set<UUID> onlineParticipantIds
) {
    public static PresenceSnapshot of(UUID roomId, Set<UUID> onlineParticipantIds) {
        return new PresenceSnapshot("PRESENCE_SNAPSHOT", roomId, onlineParticipantIds);
    }
}
