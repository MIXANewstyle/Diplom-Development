package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.AdminPostUpdateRequest;
import com.diplom.contentservice.dto.PostResponse;
import com.diplom.contentservice.security.CustomUserDetails;
import com.diplom.contentservice.service.AdminPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/admin/posts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPostController {

    private final AdminPostService adminPostService;

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> moderate(
        @AuthenticationPrincipal CustomUserDetails admin,
        @PathVariable UUID postId
    ) {
        adminPostService.moderatePost(postId, admin.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<PostResponse> edit(
        @AuthenticationPrincipal CustomUserDetails admin,
        @PathVariable UUID postId,
        @Valid @RequestBody AdminPostUpdateRequest request
    ) {
        return ResponseEntity.ok(adminPostService.editPost(postId, request, admin.getId()));
    }
}
