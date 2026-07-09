package com.diplom.userservice.controller;

import com.diplom.userservice.dto.PasswordResetRequest;
import com.diplom.userservice.dto.RoleUpdateRequest;
import com.diplom.userservice.dto.StatusUpdateRequest;
import com.diplom.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import com.diplom.userservice.dto.AdminUserSummaryResponse;
import com.diplom.userservice.dto.AdminUserDetailsResponse;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminModerationController {

    private final UserService userService;

    @PutMapping("/{userId}/role")
    public ResponseEntity<Void> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody RoleUpdateRequest request) {
        userService.updateUserRole(userId, request.roleId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody StatusUpdateRequest request) {
        userService.updateUserStatus(userId, request.statusId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody PasswordResetRequest request) {
        userService.resetUserPassword(userId, request.password());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<AdminUserSummaryResponse>> searchUsers(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.searchAdminUsers(query, page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserDetailsResponse> getUserDetails(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getAdminUserDetails(userId));
    }
}
