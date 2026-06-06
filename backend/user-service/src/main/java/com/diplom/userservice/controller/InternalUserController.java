package com.diplom.userservice.controller;

import com.diplom.userservice.entity.UserProfile;
import com.diplom.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserProfileRepository userProfileRepository;

    @GetMapping("/{userId}/psych-profile")
    public ResponseEntity<String> getPsychProfile(@PathVariable UUID userId) {
        String profileStr = userProfileRepository.findById(userId)
                .map(UserProfile::getPsychProfile)
                .orElse(null);

        String responseBody = (profileStr != null && !profileStr.trim().isEmpty()) ? profileStr : "{}";
        
        log.info("Returned psych_profile for userId: {}, status: 200", userId);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(responseBody);
    }
}
