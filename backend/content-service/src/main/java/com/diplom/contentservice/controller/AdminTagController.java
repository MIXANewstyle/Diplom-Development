package com.diplom.contentservice.controller;

import com.diplom.contentservice.dto.TagCreateRequest;
import com.diplom.contentservice.dto.TagResponse;
import com.diplom.contentservice.entity.ModerationAction;
import com.diplom.contentservice.entity.ModerationTargetType;
import com.diplom.contentservice.security.CustomUserDetails;
import com.diplom.contentservice.service.ModerationLogService;
import com.diplom.contentservice.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/admin/tags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTagController {

    private final TagService tagService;
    private final ModerationLogService moderationLogService;

    @PostMapping
    public ResponseEntity<TagResponse> create(
        @AuthenticationPrincipal CustomUserDetails admin,
        @Valid @RequestBody TagCreateRequest request
    ) {
        TagResponse response = tagService.createTag(request);
        
        Map<String, Object> after = Map.of(
            "id", response.id().toString(),
            "name", response.name()
        );
        moderationLogService.log(
            admin.getId(),
            ModerationAction.TAG_CREATED,
            ModerationTargetType.TAG,
            response.id(),
            null,
            after
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal CustomUserDetails admin,
        @PathVariable UUID tagId
    ) {
        tagService.deleteTag(tagId);
        
        Map<String, Object> before = Map.of(
            "id", tagId.toString()
        );
        moderationLogService.log(
            admin.getId(),
            ModerationAction.TAG_DELETED,
            ModerationTargetType.TAG,
            tagId,
            before,
            null
        );
        
        return ResponseEntity.noContent().build();
    }
}
