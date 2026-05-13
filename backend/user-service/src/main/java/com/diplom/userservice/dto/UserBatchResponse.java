package com.diplom.userservice.dto;

import java.util.UUID;

public record UserBatchResponse(
        UUID id,
        String username,
        String avatarUrl
) {}
