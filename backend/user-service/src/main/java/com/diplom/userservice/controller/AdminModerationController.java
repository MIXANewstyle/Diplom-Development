package com.diplom.userservice.controller;

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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminModerationController {

    private final UserService userService;

    @PutMapping("/{userId}/role")
    public ResponseEntity<Void> updateRole(
            @PathVariable UUID userId,
            @RequestBody RoleUpdateRequest request) {
        userService.updateUserRole(userId, request.roleId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID userId,
            @RequestBody StatusUpdateRequest request) {
        userService.updateUserStatus(userId, request.statusId());
        return ResponseEntity.ok().build();
    }
}
