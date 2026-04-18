package com.diplom.userservice.service;

import com.diplom.userservice.dto.UserRegistrationRequest;
import com.diplom.userservice.dto.UserResponse;
import com.diplom.userservice.entity.User;
import com.diplom.userservice.entity.UserProfile;
import com.diplom.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse registerUser(UserRegistrationRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("Email is already taken");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(request.password()) // Placeholder, to be hashed later
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

        return new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getProfile().getUsername()
        );
    }
}
