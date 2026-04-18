package com.diplom.userservice.dto;

import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String username
) {}
