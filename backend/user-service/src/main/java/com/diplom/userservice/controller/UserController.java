package com.diplom.userservice.controller;

import com.diplom.userservice.dto.JwtResponse;
import com.diplom.userservice.dto.LoginRequest;
import com.diplom.userservice.dto.MyProfileResponse;
import com.diplom.userservice.dto.ProfileUpdateRequest;
import com.diplom.userservice.dto.PublicProfileResponse;
import com.diplom.userservice.dto.UserRegistrationRequest;
import com.diplom.userservice.dto.UserResponse;
import com.diplom.userservice.dto.UserBatchRequest;
import com.diplom.userservice.dto.UserBatchResponse;
import com.diplom.userservice.dto.FollowedAuthorResponse;
import com.diplom.userservice.entity.User;
import com.diplom.userservice.repository.UserRepository;
import com.diplom.userservice.security.JwtService;
import java.util.UUID;
import java.util.List;
import com.diplom.userservice.service.UserService;
import com.diplom.userservice.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.diplom.userservice.security.CustomUserDetails;
import com.diplom.userservice.exception.InvalidCredentialsException;
import com.diplom.userservice.exception.UserNotFoundException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SocialService socialService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        UserResponse response = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRoleId());
        return ResponseEntity.ok(new JwtResponse(token));
    }

    @PostMapping("/me/token")
    public ResponseEntity<JwtResponse> refreshToken(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRoleId());
        return ResponseEntity.ok(new JwtResponse(token));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<Void> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProfileUpdateRequest request) {
        userService.updateProfile(userDetails.getId(), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/profile")
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getMyProfile(userDetails.getId()));
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<PublicProfileResponse> getPublicProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getPublicProfile(userId));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<UserBatchResponse>> getBatchProfiles(@Valid @RequestBody UserBatchRequest request) {
        List<UserBatchResponse> response = userService.getBatchProfiles(request.ids());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/follows")
    public ResponseEntity<List<FollowedAuthorResponse>> getFollowedAuthors(@PathVariable UUID userId) {
        List<FollowedAuthorResponse> response = socialService.getFollowedAuthors(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserBatchResponse>> searchUsers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String username) {
        return ResponseEntity.ok(userService.searchByUsername(username, userDetails.getId()));
    }
}
