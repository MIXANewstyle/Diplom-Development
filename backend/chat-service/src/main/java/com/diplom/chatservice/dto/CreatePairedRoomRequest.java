package com.diplom.chatservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePairedRoomRequest(
    @NotNull InviteMode inviteMode,
    UUID friendUserId
) {}
