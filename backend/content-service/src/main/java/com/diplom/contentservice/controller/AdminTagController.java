package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.TagCreateRequest;
import com.diplom.contentservice.dto.TagResponse;
import com.diplom.contentservice.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/admin/tags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTagController {

    private final TagService tagService;

    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagCreateRequest request) {
        TagResponse tag = tagService.createTag(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tag);
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> delete(@PathVariable UUID tagId) {
        tagService.deleteTag(tagId);
        return ResponseEntity.noContent().build();
    }
}
