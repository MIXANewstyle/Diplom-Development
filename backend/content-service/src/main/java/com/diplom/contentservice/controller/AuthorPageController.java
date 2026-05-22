package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.FeedPageResponse;
import com.diplom.contentservice.feed.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/authors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BASIC')")
public class AuthorPageController {

    private final FeedService feedService;

    @GetMapping("/{authorId}/posts")
    public ResponseEntity<FeedPageResponse> getAuthorPosts(
        @PathVariable UUID authorId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) List<UUID> tagIds
    ) {
        return ResponseEntity.ok(
            feedService.getAuthorPosts(authorId, cursor, pageSize, tagIds));
    }
}
