package com.diplom.userservice.controller;

import com.diplom.userservice.dto.UserBatchRequest;
import com.diplom.userservice.dto.UserBatchResponse;
import com.diplom.userservice.entity.UserProfile;
import com.diplom.userservice.repository.UserProfileRepository;
import com.diplom.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserProfileRepository userProfileRepository;
    private final UserService userService;

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

    /**
     * Internal (API-key-authenticated) batch profile lookup.
     * Same request/response shape as the public POST /api/v1/users/batch.
     * Used by chat-service for unauthenticated paths (e.g. invite landing).
     */
    @PostMapping("/batch")
    public ResponseEntity<List<UserBatchResponse>> batchProfiles(
            @Valid @RequestBody UserBatchRequest request) {
        log.info("Internal batch profile lookup for {} user(s)", request.ids().size());
        List<UserBatchResponse> response = userService.getBatchProfiles(request.ids());
        return ResponseEntity.ok(response);
    }
}
