package com.diplom.chatservice.dto.invite;

import java.time.OffsetDateTime;

public record MintInviteResponse(String token, OffsetDateTime expiresAt) {}
