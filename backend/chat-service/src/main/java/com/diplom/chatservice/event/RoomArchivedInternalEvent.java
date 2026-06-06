package com.diplom.chatservice.event;

import java.util.UUID;

public record RoomArchivedInternalEvent(
        UUID roomId,
        int typeId
) {}
