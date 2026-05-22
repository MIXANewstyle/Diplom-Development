package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.FeedPageResponse;
import com.diplom.contentservice.feed.FeedService;
import com.diplom.contentservice.feed.SortMode;
import com.diplom.contentservice.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BASIC')")
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    public ResponseEntity<FeedPageResponse> getFeed(
        @AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(required = false, defaultValue = "newest") String sort,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) List<UUID> tagIds
    ) {
        SortMode mode = SortMode.fromQueryParam(sort);
        return ResponseEntity.ok(
            feedService.getFeed(mode, cursor, pageSize, tagIds, user.getId()));
    }
}
