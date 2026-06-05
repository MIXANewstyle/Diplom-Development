package com.diplom.chatservice.dto;

import jakarta.validation.constraints.NotNull;

public record CreateSoloRoomRequest(
    @NotNull SoloMode mode
) {}
