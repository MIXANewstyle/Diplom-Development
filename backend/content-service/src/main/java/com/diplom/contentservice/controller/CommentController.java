package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.CommentCreateRequest;
import com.diplom.contentservice.dto.CommentPageResponse;
import com.diplom.contentservice.dto.CommentResponse;
import com.diplom.contentservice.dto.CommentUpdateRequest;
import com.diplom.contentservice.security.CustomUserDetails;
import com.diplom.contentservice.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    @PreAuthorize("hasRole('BASIC')")
    public ResponseEntity<CommentResponse> create(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID postId,
        @Valid @RequestBody CommentCreateRequest request
    ) {
        CommentResponse response = commentService.createComment(postId, request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/posts/{postId}/comments")
    @PreAuthorize("hasRole('BASIC')")
    public ResponseEntity<CommentPageResponse> getRoots(
        @PathVariable UUID postId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(commentService.getRootComments(postId, cursor, pageSize));
    }

    @GetMapping("/comments/{commentId}/replies")
    @PreAuthorize("hasRole('BASIC')")
    public ResponseEntity<CommentPageResponse> getReplies(
        @PathVariable UUID commentId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(commentService.getReplies(commentId, cursor, pageSize));
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<CommentResponse> update(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID commentId,
        @Valid @RequestBody CommentUpdateRequest request
    ) {
        CommentResponse response = commentService.updateComment(commentId, request, user.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID commentId
    ) {
        commentService.deleteComment(commentId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
