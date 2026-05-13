package com.diplom.userservice.service;

import com.diplom.userservice.dto.UserRegistrationRequest;
import com.diplom.userservice.dto.UserResponse;
import com.diplom.userservice.dto.UserBatchResponse;
import com.diplom.userservice.entity.User;
import com.diplom.userservice.entity.UserOutboxEvent;
import com.diplom.userservice.entity.UserProfile;
import com.diplom.userservice.dto.ProfileUpdateRequest;
import com.diplom.userservice.event.AccountModeratedEvent;
import com.diplom.userservice.event.EventType;
import com.diplom.userservice.event.ProfileChangedEvent;
import com.diplom.userservice.event.RoleUpdatedEvent;
import com.diplom.userservice.event.UserRegisteredEvent;
import com.diplom.userservice.exception.EmailAlreadyTakenException;
import com.diplom.userservice.exception.UserNotFoundException;
import com.diplom.userservice.exception.UserProfileNotFoundException;
import com.diplom.userservice.outbox.OutboxEventFactory;
import com.diplom.userservice.repository.UserOutboxEventRepository;
import com.diplom.userservice.repository.UserProfileRepository;
import com.diplom.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserOutboxEventRepository userOutboxEventRepository;
    private final UserProfileRepository userProfileRepository;
    private final OutboxEventFactory outboxEventFactory;

    @Transactional
    public UserResponse registerUser(UserRegistrationRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyTakenException("Email " + request.email() + " is already taken");
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
        UserRegisteredEvent payloadDto = new UserRegisteredEvent(savedUser.getId(), savedUser.getEmail(), OffsetDateTime.now());
        UserOutboxEvent outboxEvent = outboxEventFactory.create(EventType.USER_REGISTERED, payloadDto);
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
                .orElseThrow(() -> new UserProfileNotFoundException("Profile for user " + userId + " not found"));

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

        if (request.contactInfo() != null && !request.contactInfo().equals(profile.getContactInfo())) {
            profile.setContactInfo(request.contactInfo());
        }

        if (request.birthDate() != null && !request.birthDate().equals(profile.getBirthDate())) {
            profile.setBirthDate(request.birthDate());
        }

        if (request.genderId() != null && !request.genderId().equals(profile.getGenderId())) {
            profile.setGenderId(request.genderId());
        }

        profile.setUpdatedAt(OffsetDateTime.now());
        userProfileRepository.save(profile);

        if (isProfileChanged) {
            ProfileChangedEvent payloadDto = new ProfileChangedEvent(userId, profile.getUsername(), profile.getFullName(), profile.getAvatarUrl(), OffsetDateTime.now());
            UserOutboxEvent event = outboxEventFactory.create(EventType.PROFILE_CHANGED, payloadDto);
            userOutboxEventRepository.save(event);
        }
    }
    @Transactional
    public void updateUserRole(UUID userId, Integer roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User " + userId + " not found"));
        user.setRoleId(roleId);
        userRepository.save(user);

        RoleUpdatedEvent payloadDto = new RoleUpdatedEvent(userId, roleId, OffsetDateTime.now());
        UserOutboxEvent event = outboxEventFactory.create(EventType.ROLE_UPDATED, payloadDto);
        userOutboxEventRepository.save(event);
    }

    @Transactional
    public void updateUserStatus(UUID userId, Integer statusId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User " + userId + " not found"));
        user.setStatusId(statusId);
        userRepository.save(user);

        AccountModeratedEvent payloadDto = new AccountModeratedEvent(userId, statusId, OffsetDateTime.now());
        UserOutboxEvent event = outboxEventFactory.create(EventType.ACCOUNT_MODERATED, payloadDto);
        userOutboxEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<UserBatchResponse> getBatchProfiles(List<UUID> ids) {
        List<UUID> dedupedIds = ids.stream().distinct().toList();
        List<UserProfile> profiles = userProfileRepository.findAllByUserIdIn(dedupedIds);
        return profiles.stream()
                .map(p -> new UserBatchResponse(p.getUser().getId(), p.getUsername(), p.getAvatarUrl()))
                .toList();
    }
}
