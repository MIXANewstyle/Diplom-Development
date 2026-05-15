package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.TagResponse;
import com.diplom.contentservice.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<Page<TagResponse>> listOrSearch(
        @RequestParam(required = false) String prefix,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<TagResponse> result = (prefix == null || prefix.isBlank())
            ? tagService.listTags(pageable)
            : tagService.searchByPrefix(prefix, pageable);
        return ResponseEntity.ok(result);
    }
}
