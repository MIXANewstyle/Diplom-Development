package com.diplom.userservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FriendshipAcceptedEvent(
        UUID requesterId,
        UUID addresseeId,
        OffsetDateTime occurredAt
) {}
