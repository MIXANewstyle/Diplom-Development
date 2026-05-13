package com.diplom.userservice.controller;

import com.diplom.userservice.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.diplom.userservice.security.CustomUserDetails;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @PostMapping("/me/follow/{authorId}")
    public ResponseEntity<Void> followAuthor(
            @AuthenticationPrincipal CustomUserDetails userDetails, 
            @PathVariable UUID authorId) {
        socialService.followAuthor(userDetails.getId(), authorId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/me/follow/{authorId}")
    public ResponseEntity<Void> unfollowAuthor(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID authorId) {
        socialService.unfollowAuthor(userDetails.getId(), authorId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/me/friends/request/{addresseeId}")
    public ResponseEntity<Void> sendFriendRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails, 
            @PathVariable UUID addresseeId) {
        socialService.sendFriendRequest(userDetails.getId(), addresseeId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/me/friends/{requesterId}/accept")
    public ResponseEntity<Void> acceptFriendRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID requesterId) {
        socialService.acceptFriendRequest(userDetails.getId(), requesterId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/me/friends/{requesterId}/decline")
    public ResponseEntity<Void> declineFriendRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID requesterId) {
        socialService.declineFriendRequest(userDetails.getId(), requesterId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me/friends/{addresseeId}")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID addresseeId) {
        socialService.cancelFriendRequest(userDetails.getId(), addresseeId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
