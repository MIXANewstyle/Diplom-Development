package com.diplom.userservice.dto;

public record UserRegistrationRequest(
    String email,
    String password,
    String username,
    String fullName
) {}
