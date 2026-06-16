package com.diplom.chatservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitTurnRequest(
    @NotBlank
    @Size(max = 2000)
    String text
) {}
