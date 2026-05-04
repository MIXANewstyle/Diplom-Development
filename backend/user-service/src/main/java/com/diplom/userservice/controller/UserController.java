package com.diplom.userservice.controller;

import com.diplom.userservice.dto.JwtResponse;
import com.diplom.userservice.dto.LoginRequest;
import com.diplom.userservice.dto.ProfileUpdateRequest;
import com.diplom.userservice.dto.UserRegistrationRequest;
import com.diplom.userservice.dto.UserResponse;
import com.diplom.userservice.entity.User;
import com.diplom.userservice.repository.UserRepository;
import com.diplom.userservice.security.JwtService;
import java.util.UUID;
import com.diplom.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.diplom.userservice.security.CustomUserDetails;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody UserRegistrationRequest request) {
        UserResponse response = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRoleId());
        return ResponseEntity.ok(new JwtResponse(token));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<Void> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ProfileUpdateRequest request) {
        userService.updateProfile(userDetails.getId(), request);
        return ResponseEntity.ok().build();
    }
}
