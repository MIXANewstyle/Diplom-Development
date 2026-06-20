package com.diplom.chatservice.dto;

import jakarta.validation.constraints.Size;

public record RenameRoomRequest(
    @Size(max = 100) String title
) {}
