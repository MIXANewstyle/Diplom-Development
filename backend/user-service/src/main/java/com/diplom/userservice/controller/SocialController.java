package com.diplom.userservice.controller;

import com.diplom.userservice.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @PostMapping("/{followerId}/follow/{authorId}")
    public ResponseEntity<Void> followAuthor(
            @PathVariable UUID followerId, 
            @PathVariable UUID authorId) {
        socialService.followAuthor(followerId, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{requesterId}/friends/request/{addresseeId}")
    public ResponseEntity<Void> sendFriendRequest(
            @PathVariable UUID requesterId, 
            @PathVariable UUID addresseeId) {
        socialService.sendFriendRequest(requesterId, addresseeId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
