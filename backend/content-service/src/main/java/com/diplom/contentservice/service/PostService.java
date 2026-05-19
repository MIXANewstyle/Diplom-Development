package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.PostCreateRequest;
import com.diplom.contentservice.dto.PostResponse;
import com.diplom.contentservice.dto.PostUpdateRequest;
import com.diplom.contentservice.dto.UserBatchResponse;
import com.diplom.contentservice.entity.ContentOutboxEvent;
import com.diplom.contentservice.entity.Post;
import com.diplom.contentservice.entity.PostStatus;
import com.diplom.contentservice.entity.Tag;
import com.diplom.contentservice.event.EventType;
import com.diplom.contentservice.event.PostArchivedEvent;
import com.diplom.contentservice.event.PostPublishedEvent;
import com.diplom.contentservice.exception.InvalidPostStateException;
import com.diplom.contentservice.exception.InvalidPublicationException;
import com.diplom.contentservice.exception.InvalidTagReferenceException;
import com.diplom.contentservice.exception.NotPostAuthorException;
import com.diplom.contentservice.exception.PostNotFoundException;
import com.diplom.contentservice.outbox.OutboxEventFactory;
import com.diplom.contentservice.repository.ContentOutboxEventRepository;
import com.diplom.contentservice.repository.PostRepository;
import com.diplom.contentservice.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final PostMapper postMapper;
    private final OutboxEventFactory outboxEventFactory;
    private final ContentOutboxEventRepository outboxEventRepository;
    private final ProfileCacheService profileCacheService;

    @Transactional
    public PostResponse createDraft(PostCreateRequest request, UUID authorId) {
        Post post = Post.builder()
                .authorId(authorId)
                .title(request.title().trim())
                .content(request.content())
                .coverImageUrl(request.coverImageUrl())
                .statusId(PostStatus.DRAFT.getId())
                .viewsCount(0)
                .upvotesCount(0)
                .commentsCount(0)
                .tags(resolveTagsOrThrow(request.tagIds()))
                .keywords(normalizeKeywords(request.keywords()))
                .build();

        post = postRepository.save(post);
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(Set.of(post.getAuthorId()));
        return postMapper.toResponse(post, profiles.get(post.getAuthorId()));
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(UUID postId, UUID currentUserId, String currentUserRole) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        PostStatus status = PostStatus.fromId(post.getStatusId());
        boolean isAuthor = post.getAuthorId().equals(currentUserId);
        boolean isAdmin = "ADMIN".equals(currentUserRole);

        switch (status) {
            case PUBLISHED -> {
                if (!isAuthor && !isAdmin) {
                    if (!isSubscribedRole(currentUserRole)) {
                        throw new AccessDeniedException("Access denied");
                    }
                }
            }
            case DRAFT, ARCHIVED -> {
                if (!isAuthor && !isAdmin) {
                    throw new PostNotFoundException("Post not found: " + postId);
                }
            }
            case MODERATED -> {
                if (!isAdmin) {
                    throw new PostNotFoundException("Post not found: " + postId);
                }
            }
        }

        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(Set.of(post.getAuthorId()));
        return postMapper.toResponse(post, profiles.get(post.getAuthorId()));
    }

    @Transactional
    public PostResponse updatePost(UUID postId, PostUpdateRequest request, UUID currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        if (!post.getAuthorId().equals(currentUserId)) {
            throw new NotPostAuthorException("You are not the author of this post");
        }

        PostStatus status = PostStatus.fromId(post.getStatusId());

        if (status == PostStatus.MODERATED) {
            throw new InvalidPostStateException("Moderated posts cannot be edited");
        }

        if (status == PostStatus.PUBLISHED && request.coverImageUrl() != null) {
            throw new InvalidPostStateException("Cover image cannot be edited after publication");
        }

        if (request.title() != null) {
            post.setTitle(request.title().trim());
        }
        if (request.content() != null) {
            post.setContent(request.content());
        }
        if (request.coverImageUrl() != null) {
            post.setCoverImageUrl(request.coverImageUrl());
        }
        if (request.tagIds() != null) {
            post.setTags(resolveTagsOrThrow(request.tagIds()));
        }
        if (request.keywords() != null) {
            post.setKeywords(normalizeKeywords(request.keywords()));
        }

        post = postRepository.save(post);
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(Set.of(post.getAuthorId()));
        return postMapper.toResponse(post, profiles.get(post.getAuthorId()));
    }

    @Transactional
    public void deleteDraft(UUID postId, UUID currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        if (!post.getAuthorId().equals(currentUserId)) {
            throw new NotPostAuthorException("You are not the author of this post");
        }

        PostStatus status = PostStatus.fromId(post.getStatusId());
        if (status != PostStatus.DRAFT) {
            throw new InvalidPostStateException("Only drafts can be deleted; published posts must be archived");
        }

        postRepository.delete(post);
    }

    @Transactional
    public PostResponse publishPost(UUID postId, UUID currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        if (!post.getAuthorId().equals(currentUserId)) {
            throw new NotPostAuthorException("You are not the author of this post");
        }

        PostStatus status = PostStatus.fromId(post.getStatusId());
        if (status == PostStatus.PUBLISHED) {
            throw new InvalidPostStateException("Post is already published");
        }
        if (status == PostStatus.MODERATED) {
            throw new InvalidPostStateException("Moderated posts cannot be published by the author");
        }

        // Pre-publish validation
        if (post.getTitle() == null || post.getTitle().isBlank()) {
            throw new InvalidPublicationException("Cannot publish: title is empty");
        }
        if (post.getContent() == null || post.getContent().isBlank()) {
            throw new InvalidPublicationException("Cannot publish: content is empty");
        }
        if (post.getTags() == null || post.getTags().isEmpty()) {
            throw new InvalidPublicationException("Cannot publish: at least one tag is required");
        }

        post.setStatusId(PostStatus.PUBLISHED.getId());
        if (post.getPublishedAt() == null) {
            post.setPublishedAt(OffsetDateTime.now());
        }

        post = postRepository.save(post);

        PostPublishedEvent payload = new PostPublishedEvent(
                post.getId(),
                post.getAuthorId(),
                post.getPublishedAt(),
                OffsetDateTime.now()
        );
        ContentOutboxEvent event = outboxEventFactory.create(EventType.POST_PUBLISHED, payload);
        outboxEventRepository.save(event);

        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(Set.of(post.getAuthorId()));
        return postMapper.toResponse(post, profiles.get(post.getAuthorId()));
    }

    @Transactional
    public PostResponse archivePost(UUID postId, UUID currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        if (!post.getAuthorId().equals(currentUserId)) {
            throw new NotPostAuthorException("You are not the author of this post");
        }

        PostStatus status = PostStatus.fromId(post.getStatusId());
        if (status != PostStatus.PUBLISHED) {
            throw new InvalidPostStateException("Only published posts can be archived");
        }

        post.setStatusId(PostStatus.ARCHIVED.getId());
        post = postRepository.save(post);

        PostArchivedEvent payload = new PostArchivedEvent(
                post.getId(),
                post.getAuthorId(),
                OffsetDateTime.now()
        );
        ContentOutboxEvent event = outboxEventFactory.create(EventType.POST_ARCHIVED, payload);
        outboxEventRepository.save(event);

        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(Set.of(post.getAuthorId()));
        return postMapper.toResponse(post, profiles.get(post.getAuthorId()));
    }

    private Set<Tag> resolveTagsOrThrow(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        List<Tag> found = tagRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new InvalidTagReferenceException("One or more tags do not exist");
        }
        return new HashSet<>(found);
    }

    private List<String> normalizeKeywords(List<String> raw) {
        if (raw == null) {
            return null;
        }
        return raw.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .toList();
    }

    private boolean isSubscribedRole(String role) {
        return "BASIC".equals(role) || "AUTHOR".equals(role) || "ADMIN".equals(role);
    }
}
