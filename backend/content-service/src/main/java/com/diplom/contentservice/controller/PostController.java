package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.PostCreateRequest;
import com.diplom.contentservice.dto.PostResponse;
import com.diplom.contentservice.dto.PostUpdateRequest;
import com.diplom.contentservice.dto.UpvoteResponse;
import com.diplom.contentservice.security.CustomUserDetails;
import com.diplom.contentservice.service.PostService;
import com.diplom.contentservice.service.PostUpvoteService;
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
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostUpvoteService postUpvoteService;

    @PostMapping
    @PreAuthorize("hasRole('AUTHOR')")
    public ResponseEntity<PostResponse> create(
        @AuthenticationPrincipal CustomUserDetails user,
        @Valid @RequestBody PostCreateRequest request
    ) {
        PostResponse response = postService.createDraft(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('AUTHOR')")
    public ResponseEntity<List<PostResponse>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer status) {
        return ResponseEntity.ok(postService.getMyPosts(user.getId(), status));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getById(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID postId
    ) {
        PostResponse response = postService.getPostById(postId, user.getId(), user.getRoleName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{postId}")
    @PreAuthorize("hasRole('AUTHOR')")
    public ResponseEntity<PostResponse> update(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID postId,
        @Valid @RequestBody PostUpdateRequest request
    ) {
        PostResponse response = postService.updatePost(postId, request, user.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    @PreAuthorize("hasRole('AUTHOR')")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID postId
    ) {
        postService.deleteDraft(postId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postId}/publish")
    @PreAuthorize("hasRole('AUTHOR')")
    public ResponseEntity<PostResponse> publish(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID postId
    ) {
        PostResponse response = postService.publishPost(postId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{postId}/archive")
    @PreAuthorize("hasRole('AUTHOR')")
    public ResponseEntity<PostResponse> archive(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID postId
    ) {
        PostResponse response = postService.archivePost(postId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{postId}/upvote")
    @PreAuthorize("hasRole('BASIC')")
    public ResponseEntity<UpvoteResponse> toggleUpvote(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID postId
    ) {
        return ResponseEntity.ok(postUpvoteService.toggleUpvote(postId, user.getId()));
    }
}
