package com.diplom.chatservice.dto.ws;

import java.util.UUID;

/**
 * Outbound payload broadcast to {@code /topic/rooms/{roomId}} when a participant
 * connects or disconnects. Also sent as a targeted message to a newcomer
 * (via {@code /user/queue/presence}) with the full current presence set.
 *
 * <p>The {@code type} field enables client-side message dispatch.
 */
public record PresenceUpdated(
    String type,
    UUID participantId,
    String status
) {
    /** Status value for a newly connected participant. */
    public static final String ONLINE = "ONLINE";
    /** Status value for a disconnected participant. */
    public static final String OFFLINE = "OFFLINE";

    public static PresenceUpdated online(UUID participantId) {
        return new PresenceUpdated("PRESENCE_UPDATED", participantId, ONLINE);
    }

    public static PresenceUpdated offline(UUID participantId) {
        return new PresenceUpdated("PRESENCE_UPDATED", participantId, OFFLINE);
    }
}
