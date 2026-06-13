package com.diplom.chatservice.dto;

import java.util.UUID;

public record UserBatchResponse(
    UUID id,
    String username,
    String fullName,
    String avatarUrl
) {
    public String getDisplayName() {
        return (fullName != null && !fullName.isBlank()) ? fullName : username;
    }
}
