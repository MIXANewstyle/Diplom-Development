package com.diplom.chatservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePairedRoomRequest(
    @NotNull InviteMode inviteMode,
    UUID friendUserId,
    UUID seedContextRoomId,
    @Size(max = 100) String title
) {}
