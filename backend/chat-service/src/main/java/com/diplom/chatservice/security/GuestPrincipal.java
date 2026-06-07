package com.diplom.chatservice.security;

import lombok.Getter;

import java.security.Principal;
import java.util.UUID;

@Getter
public class GuestPrincipal implements Principal {
    private final UUID participantId;
    private final UUID roomId;

    public GuestPrincipal(UUID participantId, UUID roomId) {
        this.participantId = participantId;
        this.roomId = roomId;
    }

    @Override
    public String getName() {
        return participantId.toString();
    }
}
