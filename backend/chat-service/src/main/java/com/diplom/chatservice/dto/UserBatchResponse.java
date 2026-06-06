package com.diplom.chatservice.dto;

import java.util.UUID;

public record UserBatchResponse(
    UUID id,
    String username,
    String avatarUrl
) {}
