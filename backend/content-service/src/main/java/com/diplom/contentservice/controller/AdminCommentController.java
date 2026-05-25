package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.AdminCommentUpdateRequest;
import com.diplom.contentservice.dto.CommentResponse;
import com.diplom.contentservice.security.CustomUserDetails;
import com.diplom.contentservice.service.AdminCommentService;
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
@RequestMapping("/internal/v1/admin/comments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> moderate(
        @AuthenticationPrincipal CustomUserDetails admin,
        @PathVariable UUID commentId
    ) {
        adminCommentService.moderateComment(commentId, admin.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentResponse> edit(
        @AuthenticationPrincipal CustomUserDetails admin,
        @PathVariable UUID commentId,
        @Valid @RequestBody AdminCommentUpdateRequest request
    ) {
        return ResponseEntity.ok(
            adminCommentService.editComment(commentId, request, admin.getId()));
    }
}
