package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.CounterDeltas;
import com.diplom.contentservice.dto.PostResponse;
import com.diplom.contentservice.dto.TagResponse;
import com.diplom.contentservice.dto.UserBatchResponse;
import com.diplom.contentservice.entity.Post;
import com.diplom.contentservice.entity.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostMapper {

    public PostResponse toResponse(Post post, UserBatchResponse authorProfile, CounterDeltas deltas) {
        long upvotes = post.getUpvotesCount() + deltas.upvotes();
        long comments = post.getCommentsCount() + deltas.comments();
        long views = post.getViewsCount() + deltas.views();
        return new PostResponse(
                post.getId(),
                post.getAuthorId(),
                authorProfile == null ? null : authorProfile.username(),
                authorProfile == null ? null : authorProfile.avatarUrl(),
                post.getTitle(),
                post.getContent(),
                post.getCoverImageUrl(),
                PostStatus.fromId(post.getStatusId()).name(),
                post.getPublishedAt(),
                post.getUpdatedAt(),
                (int) Math.max(0, views),
                (int) Math.max(0, upvotes),
                (int) Math.max(0, comments),
                tagDtos(post),
                keywordsOrEmpty(post),
                post.getVersion()
        );
    }

    public List<PostResponse> toResponses(
            List<Post> posts,
            Map<UUID, UserBatchResponse> profiles,
            Map<UUID, CounterDeltas> deltas
    ) {
        if (posts == null) {
            return List.of();
        }
        return posts.stream()
                .map(p -> toResponse(
                        p,
                        profiles != null ? profiles.get(p.getAuthorId()) : null,
                        deltas != null ? deltas.getOrDefault(p.getId(), CounterDeltas.zero()) : CounterDeltas.zero()
                ))
                .collect(Collectors.toList());
    }

    private Set<TagResponse> tagDtos(Post post) {
        return post.getTags() == null
                ? Set.of()
                : post.getTags().stream()
                        .map(t -> new TagResponse(t.getId(), t.getName()))
                        .collect(Collectors.toSet());
    }

    private List<String> keywordsOrEmpty(Post post) {
        return post.getKeywords() == null ? List.of() : post.getKeywords();
    }
}
