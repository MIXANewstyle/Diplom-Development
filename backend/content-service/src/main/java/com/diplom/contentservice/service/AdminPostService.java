package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.AdminPostUpdateRequest;
import com.diplom.contentservice.dto.CounterDeltas;
import com.diplom.contentservice.dto.PostResponse;
import com.diplom.contentservice.dto.UserBatchResponse;
import com.diplom.contentservice.entity.ModerationAction;
import com.diplom.contentservice.entity.ModerationTargetType;
import com.diplom.contentservice.entity.Post;
import com.diplom.contentservice.entity.PostStatus;
import com.diplom.contentservice.entity.Tag;
import com.diplom.contentservice.event.EventType;
import com.diplom.contentservice.event.PostModeratedEvent;
import com.diplom.contentservice.exception.InvalidTagReferenceException;
import com.diplom.contentservice.exception.PostNotFoundException;
import com.diplom.contentservice.outbox.OutboxEventFactory;
import com.diplom.contentservice.repository.ContentOutboxEventRepository;
import com.diplom.contentservice.repository.PostRepository;
import com.diplom.contentservice.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPostService {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final ContentOutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final ModerationLogService moderationLogService;
    private final ProfileCacheService profileCacheService;
    private final CounterService counterService;
    private final PostMapper postMapper;

    @Transactional
    public void moderatePost(UUID postId, UUID adminId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException("Post " + postId + " not found"));

        Map<String, Object> before = snapshotPost(post);

        post.setStatusId(PostStatus.MODERATED.getId());
        postRepository.save(post);

        moderationLogService.log(
            adminId, ModerationAction.POST_DELETED, ModerationTargetType.POST,
            postId, before, null);

        outboxEventRepository.save(outboxEventFactory.create(
            EventType.POST_MODERATED,
            new PostModeratedEvent(postId, adminId,
                ModerationAction.POST_DELETED.name(), OffsetDateTime.now())
        ));
    }

    @Transactional
    public PostResponse editPost(UUID postId, AdminPostUpdateRequest request, UUID adminId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostNotFoundException("Post " + postId + " not found"));

        Map<String, Object> before = snapshotPost(post);

        if (request.title() != null)         post.setTitle(request.title().trim());
        if (request.content() != null)       post.setContent(request.content());
        if (request.coverImageUrl() != null) post.setCoverImageUrl(request.coverImageUrl());
        if (request.tagIds() != null) {
            Set<Tag> tags = resolveTagsOrThrow(request.tagIds());
            post.setTags(tags);
        }
        if (request.keywords() != null) {
            post.setKeywords(normalizeKeywords(request.keywords()));
        }
        if (request.statusId() != null) {
            post.setStatusId(request.statusId());
            if (request.statusId() == PostStatus.PUBLISHED.getId()
                && post.getPublishedAt() == null) {
                post.setPublishedAt(OffsetDateTime.now());
            }
        }

        postRepository.save(post);

        Map<String, Object> after = snapshotPost(post);

        moderationLogService.log(
            adminId, ModerationAction.POST_EDITED, ModerationTargetType.POST,
            postId, before, after);

        outboxEventRepository.save(outboxEventFactory.create(
            EventType.POST_MODERATED,
            new PostModeratedEvent(postId, adminId,
                ModerationAction.POST_EDITED.name(), OffsetDateTime.now())
        ));

        UserBatchResponse profile = profileCacheService
            .getProfiles(Set.of(post.getAuthorId()))
            .get(post.getAuthorId());
        CounterDeltas deltas = counterService.getDeltas(postId);
        return postMapper.toResponse(post, profile, deltas);
    }

    private Map<String, Object> snapshotPost(Post post) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("title", post.getTitle());
        snap.put("content", post.getContent());
        snap.put("coverImageUrl", post.getCoverImageUrl());
        snap.put("statusId", post.getStatusId());
        snap.put("tagIds", post.getTags().stream().map(Tag::getId).toList());
        snap.put("keywords", post.getKeywords());
        return snap;
    }

    private Set<Tag> resolveTagsOrThrow(Set<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        List<Tag> found = tagRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new InvalidTagReferenceException("One or more tags do not exist");
        }
        return new HashSet<>(found);
    }

    private List<String> normalizeKeywords(List<String> raw) {
        return raw.stream()
            .map(String::trim).filter(s -> !s.isEmpty())
            .map(String::toLowerCase).distinct().toList();
    }
}
