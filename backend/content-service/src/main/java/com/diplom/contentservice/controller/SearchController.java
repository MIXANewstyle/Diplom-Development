package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.FeedPageResponse;
import com.diplom.contentservice.feed.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts/search")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BASIC')")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<FeedPageResponse> search(
        @RequestParam String q,
        @RequestParam(required = false) List<UUID> tagIds,
        @RequestParam(required = false) List<UUID> authorIds,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(searchService.search(
            q, tagIds, authorIds, from, to, cursor, pageSize));
    }
}
