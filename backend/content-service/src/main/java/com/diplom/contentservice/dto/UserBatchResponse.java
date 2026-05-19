package com.diplom.contentservice.dto;

import java.util.UUID;

public record UserBatchResponse(
    UUID id,
    String username,
    String avatarUrl
) {}
