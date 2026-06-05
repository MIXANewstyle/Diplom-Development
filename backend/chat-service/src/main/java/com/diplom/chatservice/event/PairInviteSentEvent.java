package com.diplom.chatservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PairInviteSentEvent(
    OffsetDateTime occurredAt,
    UUID roomId,
    UUID inviterUserId,
    UUID invitedUserId
) {}
