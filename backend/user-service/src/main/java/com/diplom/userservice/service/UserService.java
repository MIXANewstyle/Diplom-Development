package com.diplom.userservice.service;

import com.diplom.userservice.dto.UserRegistrationRequest;
import com.diplom.userservice.dto.UserResponse;
import com.diplom.userservice.entity.User;
import com.diplom.userservice.entity.UserOutboxEvent;
import com.diplom.userservice.entity.UserProfile;
import com.diplom.userservice.dto.ProfileUpdateRequest;
import com.diplom.userservice.repository.UserOutboxEventRepository;
import com.diplom.userservice.repository.UserProfileRepository;
import com.diplom.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserOutboxEventRepository userOutboxEventRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public UserResponse registerUser(UserRegistrationRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("Email is already taken");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roleId(2) // 2='FREE'
                .statusId(1) // 1='ACTIVE'
                .build();

        UserProfile profile = UserProfile.builder()
                .user(user)
                .username(request.username())
                .fullName(request.fullName())
                .updatedAt(OffsetDateTime.now())
                .build();

        user.setProfile(profile);

        User savedUser = userRepository.save(user);

        // Transactional Outbox
        String payload = String.format("{\"userId\":\"%s\", \"email\":\"%s\"}", savedUser.getId(), savedUser.getEmail());
        UserOutboxEvent outboxEvent = UserOutboxEvent.builder()
                .eventType("USER_REGISTERED")
                .payload(payload)
                .status("PENDING")
                .build();
        userOutboxEventRepository.save(outboxEvent);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getProfile().getUsername()
        );
    }

    @Transactional
    public void updateProfile(UUID userId, ProfileUpdateRequest request) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("UserProfile not found"));

        boolean isProfileChanged = false;

        if (request.username() != null && !request.username().equals(profile.getUsername())) {
            profile.setUsername(request.username());
            isProfileChanged = true;
        }

        if (request.fullName() != null && !request.fullName().equals(profile.getFullName())) {
            profile.setFullName(request.fullName());
            isProfileChanged = true;
        }

        if (request.avatarUrl() != null && !request.avatarUrl().equals(profile.getAvatarUrl())) {
            profile.setAvatarUrl(request.avatarUrl());
            isProfileChanged = true;
        }

        if (request.psychProfile() != null && !request.psychProfile().equals(profile.getPsychProfile())) {
            profile.setPsychProfile(request.psychProfile());
        }

        profile.setUpdatedAt(OffsetDateTime.now());
        userProfileRepository.save(profile);

        if (isProfileChanged) {
            String payload = String.format("{\"userId\":\"%s\", \"username\":\"%s\", \"fullName\":\"%s\", \"avatarUrl\":\"%s\"}",
                    userId, profile.getUsername(), profile.getFullName(), profile.getAvatarUrl());

            UserOutboxEvent event = UserOutboxEvent.builder()
                    .eventType("PROFILE_CHANGED")
                    .payload(payload)
                    .status("PENDING")
                    .build();
            userOutboxEventRepository.save(event);
        }
    }
    @Transactional
    public void updateUserRole(UUID userId, Integer roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setRoleId(roleId);
        userRepository.save(user);

        String payload = String.format("{\"userId\":\"%s\", \"roleId\":%d}", userId, roleId);
        UserOutboxEvent event = UserOutboxEvent.builder()
                .eventType("ROLE_UPDATED")
                .payload(payload)
                .status("PENDING")
                .build();
        userOutboxEventRepository.save(event);
    }

    @Transactional
    public void updateUserStatus(UUID userId, Integer statusId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setStatusId(statusId);
        userRepository.save(user);

        String payload = String.format("{\"userId\":\"%s\", \"statusId\":%d}", userId, statusId);
        UserOutboxEvent event = UserOutboxEvent.builder()
                .eventType("ACCOUNT_MODERATED")
                .payload(payload)
                .status("PENDING")
                .build();
        userOutboxEventRepository.save(event);
    }
}
