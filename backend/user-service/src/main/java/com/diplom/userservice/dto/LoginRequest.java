package com.diplom.userservice.dto;

public record LoginRequest(
        String email,
        String password
) {
}
