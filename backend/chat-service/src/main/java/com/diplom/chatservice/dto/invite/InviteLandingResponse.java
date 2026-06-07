package com.diplom.chatservice.dto.invite;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InviteLandingResponse(
        UUID roomId,
        String hostName,
        String mode,
        OffsetDateTime expiresAt
) {}
