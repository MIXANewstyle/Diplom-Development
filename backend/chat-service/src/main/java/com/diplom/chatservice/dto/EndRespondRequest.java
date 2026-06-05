package com.diplom.chatservice.dto;

import jakarta.validation.constraints.NotNull;

public record EndRespondRequest(
    @NotNull EndDecision decision
) {}
