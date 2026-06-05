package com.diplom.chatservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ParticipantResponse(
    UUID id,
    UUID userId,
    String role,
    OffsetDateTime consentStartAt,
    OffsetDateTime joinedAt,
    String guestDisplayName,
    Integer guestGenderId,
    Integer guestAge
) {}
