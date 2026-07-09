package com.diplom.userservice.service;

import com.diplom.userservice.dto.UserRegistrationRequest;
import com.diplom.userservice.dto.MyProfileResponse;
import com.diplom.userservice.dto.PublicProfileResponse;
import com.diplom.userservice.dto.UserResponse;
import com.diplom.userservice.dto.UserBatchResponse;
import com.diplom.userservice.entity.User;
import com.diplom.userservice.entity.UserRole;
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
import java.util.Objects;
import java.util.UUID;

import com.diplom.userservice.dto.AdminUserSummaryResponse;
import com.diplom.userservice.dto.AdminUserDetailsResponse;
import com.diplom.userservice.repository.FriendshipRepository;
import com.diplom.userservice.repository.AuthorFollowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserOutboxEventRepository userOutboxEventRepository;
    private final UserProfileRepository userProfileRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final FriendshipRepository friendshipRepository;
    private final AuthorFollowRepository authorFollowRepository;

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

        if (!Objects.equals(request.bio(), profile.getBio())) {
            profile.setBio(request.bio());
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

    @Transactional
    public void resetUserPassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User " + userId + " not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserBatchResponse> getBatchProfiles(List<UUID> ids) {
        List<UUID> dedupedIds = ids.stream().distinct().toList();
        List<UserProfile> profiles = userProfileRepository.findAllByUserIdIn(dedupedIds);
        return profiles.stream()
                .map(p -> new UserBatchResponse(p.getUser().getId(), p.getUsername(), p.getFullName(), p.getAvatarUrl()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException("Profile for user " + userId + " not found"));
        User user = profile.getUser();

        String roleName = UserRole.fromId(user.getRoleId()).getName();

        return new MyProfileResponse(
                profile.getId(),
                user.getEmail(),
                roleName,
                profile.getUsername(),
                profile.getFullName(),
                profile.getBio(),
                profile.getAvatarUrl(),
                profile.getContactInfo(),
                profile.getBirthDate(),
                profile.getGenderId(),
                profile.getPsychProfile(),
                profile.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException("Profile for user " + userId + " not found"));
        User user = profile.getUser();

        String roleName = UserRole.fromId(user.getRoleId()).getName();

        return new PublicProfileResponse(
                profile.getId(),
                profile.getUsername(),
                profile.getFullName(),
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getContactInfo(),
                roleName
        );
    }

    @Transactional(readOnly = true)
    public List<UserBatchResponse> searchByUsername(String username, UUID currentUserId) {
        if (username == null || username.trim().length() < 2) {
            return List.of();
        }
        
        List<UserProfile> profiles = userProfileRepository.searchByUsernameOrFullName(
                username.trim(),
                org.springframework.data.domain.PageRequest.of(0, 20));
        
        return profiles.stream()
                .filter(p -> !p.getId().equals(currentUserId))
                .map(p -> new UserBatchResponse(p.getId(), p.getUsername(), p.getFullName(), p.getAvatarUrl()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> searchAdminUsers(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        
        if (query == null || query.trim().isEmpty()) {
            return userRepository.findAll(pageRequest).map(this::toAdminSummary);
        }
        
        String trimmedQuery = query.trim();
        try {
            UUID id = UUID.fromString(trimmedQuery);
            return userRepository.findById(id)
                    .map(u -> new org.springframework.data.domain.PageImpl<>(List.of(toAdminSummary(u)), pageRequest, 1))
                    .orElseGet(() -> new org.springframework.data.domain.PageImpl<>(List.of(), pageRequest, 0));
        } catch (IllegalArgumentException e) {
            // Not a UUID, do string search
            return userRepository.searchUsers(trimmedQuery, pageRequest).map(this::toAdminSummary);
        }
    }

    private AdminUserSummaryResponse toAdminSummary(User user) {
        UserProfile profile = user.getProfile();
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getEmail(),
                profile != null ? profile.getUsername() : null,
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getAvatarUrl() : null,
                user.getRoleId(),
                user.getStatusId(),
                user.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AdminUserDetailsResponse getAdminUserDetails(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User " + userId + " not found"));
        
        UserProfile profile = user.getProfile();
        
        long friendsCount = friendshipRepository.countByRequesterIdAndStatusId(userId, 2) + 
                            friendshipRepository.countByAddresseeIdAndStatusId(userId, 2);
        long followersCount = authorFollowRepository.countByAuthorId(userId);
        long followingCount = authorFollowRepository.countByFollowerId(userId);
        
        boolean psychFilled = false;
        if (profile != null && profile.getPsychProfile() != null && !profile.getPsychProfile().trim().isEmpty() && !profile.getPsychProfile().equals("{}")) {
            psychFilled = true;
        }

        return new AdminUserDetailsResponse(
                user.getId(),
                user.getEmail(),
                user.getRoleId(),
                user.getStatusId(),
                user.getCreatedAt(),
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getUsername() : null,
                profile != null ? profile.getBio() : null,
                profile != null ? profile.getAvatarUrl() : null,
                profile != null ? profile.getContactInfo() : null,
                profile != null ? profile.getBirthDate() : null,
                profile != null ? profile.getGenderId() : null,
                profile != null ? profile.getUpdatedAt() : null,
                psychFilled,
                friendsCount,
                followersCount,
                followingCount
        );
    }
}
