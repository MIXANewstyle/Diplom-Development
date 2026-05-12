package com.diplom.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
    @Email @NotBlank String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(min = 3, max = 100) String username,
    @NotBlank @Size(min = 1, max = 255) String fullName
) {}
