package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.PostResponse;
import com.diplom.contentservice.dto.TagResponse;
import com.diplom.contentservice.entity.Post;
import com.diplom.contentservice.entity.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostMapper {

    public PostResponse toResponse(Post post) {
        Set<TagResponse> tagDtos = post.getTags() == null
            ? Set.of()
            : post.getTags().stream()
                .map(t -> new TagResponse(t.getId(), t.getName()))
                .collect(Collectors.toSet());
        return new PostResponse(
            post.getId(),
            post.getAuthorId(),
            post.getTitle(),
            post.getContent(),
            post.getCoverImageUrl(),
            PostStatus.fromId(post.getStatusId()).name(),
            post.getPublishedAt(),
            post.getUpdatedAt(),
            post.getViewsCount(),
            post.getUpvotesCount(),
            post.getCommentsCount(),
            tagDtos,
            post.getKeywords() == null ? List.of() : post.getKeywords(),
            post.getVersion()
        );
    }
}
