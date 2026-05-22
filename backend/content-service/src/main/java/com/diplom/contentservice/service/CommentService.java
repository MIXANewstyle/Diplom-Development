package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.CommentCreateRequest;
import com.diplom.contentservice.dto.CommentPageResponse;
import com.diplom.contentservice.dto.CommentResponse;
import com.diplom.contentservice.dto.CommentUpdateRequest;
import com.diplom.contentservice.dto.UserBatchResponse;
import com.diplom.contentservice.entity.Comment;
import com.diplom.contentservice.entity.ContentOutboxEvent;
import com.diplom.contentservice.entity.Post;
import com.diplom.contentservice.entity.PostStatus;
import com.diplom.contentservice.event.CommentCreatedEvent;
import com.diplom.contentservice.event.EventType;
import com.diplom.contentservice.exception.CommentEditWindowExpiredException;
import com.diplom.contentservice.exception.CommentNotFoundException;
import com.diplom.contentservice.exception.InvalidCommentParentException;
import com.diplom.contentservice.exception.InvalidCommentStateException;
import com.diplom.contentservice.exception.InvalidPostStateException;
import com.diplom.contentservice.exception.NotCommentAuthorException;
import com.diplom.contentservice.exception.PostNotFoundException;
import com.diplom.contentservice.exception.ReplyToReplyException;
import com.diplom.contentservice.outbox.OutboxEventFactory;
import com.diplom.contentservice.repository.CommentRepository;
import com.diplom.contentservice.repository.ContentOutboxEventRepository;
import com.diplom.contentservice.repository.PostRepository;
import com.diplom.contentservice.repository.RepliesCountProjection;
import com.diplom.contentservice.util.CommentCursor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(15);

    // Sentinel cursor values used when no cursor is supplied. CommentRepository's
    // cursor queries deliberately omit the `:afterCreatedAt IS NULL` branch from
    // the spec because PostgreSQL cannot determine the data type of a parameter
    // that appears only inside `? IS NULL` (Parse-time type inference fails).
    // We therefore always pass typed, non-null lower bounds; no real comment can
    // predate the epoch or carry the zero UUID, so the comparison branches match
    // everything on the unauthenticated first page just like the spec intends.
    private static final OffsetDateTime CURSOR_EPOCH =
            OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID CURSOR_MIN_UUID = new UUID(0L, 0L);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CounterService counterService;
    private final CommentMapper commentMapper;
    private final OutboxEventFactory outboxEventFactory;
    private final ContentOutboxEventRepository outboxEventRepository;
    private final ProfileCacheService profileCacheService;
    private final ModerationBlocklistService moderationBlocklistService;

    @Transactional
    public CommentResponse createComment(UUID postId, CommentCreateRequest request, UUID currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        if (post.getStatusId() != PostStatus.PUBLISHED.getId()) {
            throw new InvalidPostStateException("Comments can only be added to published posts");
        }

        if (moderationBlocklistService.isBlocked(post.getAuthorId())) {
            throw new PostNotFoundException("Post not found");
        }

        if (request.parentId() != null) {
            Comment parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new CommentNotFoundException("Parent comment not found"));

            if (!parent.getPostId().equals(postId)) {
                throw new InvalidCommentParentException("Parent comment belongs to a different post");
            }
            if (parent.getParentId() != null) {
                throw new ReplyToReplyException("Replies to replies are not supported");
            }
            if (Boolean.TRUE.equals(parent.getIsDeleted())) {
                throw new InvalidCommentStateException("Cannot reply to a deleted comment");
            }
        }

        Comment comment = Comment.builder()
                .postId(postId)
                .authorId(currentUserId)
                .parentId(request.parentId())
                .content(request.content())
                .isDeleted(false)
                .build();

        comment = commentRepository.saveAndFlush(comment);

        counterService.incrementComments(postId);

        CommentCreatedEvent payload = new CommentCreatedEvent(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                comment.getParentId(),
                OffsetDateTime.now()
        );
        ContentOutboxEvent event = outboxEventFactory.create(EventType.COMMENT_CREATED, payload);
        outboxEventRepository.save(event);

        Long repliesCount = comment.getParentId() == null ? 0L : null;
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(Set.of(comment.getAuthorId()));
        return commentMapper.toResponse(comment, repliesCount, profiles.get(comment.getAuthorId()));
    }

    @Transactional(readOnly = true)
    public CommentPageResponse getRootComments(UUID postId, String cursor, Integer pageSize) {
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("Post not found: " + postId);
        }

        int limit = clampPageSize(pageSize);
        CommentCursor parsed = CommentCursor.decode(cursor);
        OffsetDateTime afterCreatedAt = parsed == null ? CURSOR_EPOCH : parsed.createdAt();
        UUID afterId = parsed == null ? CURSOR_MIN_UUID : parsed.id();

        Pageable pageable = PageRequest.of(0, limit + 1);
        List<Comment> result = commentRepository.findRootCommentsAfter(postId, afterCreatedAt, afterId, pageable);

        boolean hasMore = result.size() > limit;
        if (hasMore) {
            result = result.subList(0, limit);
        }

        List<UUID> rootIds = result.stream().map(Comment::getId).toList();
        Map<UUID, Long> counts = rootIds.isEmpty()
                ? Map.of()
                : commentRepository.countRepliesGrouped(rootIds).stream()
                    .collect(Collectors.toMap(
                            RepliesCountProjection::getParentId,
                            RepliesCountProjection::getCnt));

        Set<UUID> authorIds = result.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(authorIds);

        List<CommentResponse> items = result.stream()
                .map(c -> commentMapper.toResponse(c, counts.getOrDefault(c.getId(), 0L), profiles.get(c.getAuthorId())))
                .toList();

        String nextCursor = null;
        if (hasMore && !result.isEmpty()) {
            Comment last = result.get(result.size() - 1);
            nextCursor = CommentCursor.encode(last.getCreatedAt(), last.getId());
        }

        return new CommentPageResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public CommentPageResponse getReplies(UUID rootCommentId, String cursor, Integer pageSize) {
        Comment root = commentRepository.findById(rootCommentId)
                .orElseThrow(() -> new CommentNotFoundException("Comment not found: " + rootCommentId));

        if (root.getParentId() != null) {
            throw new InvalidCommentParentException("Specified comment is not a root comment");
        }

        int limit = clampPageSize(pageSize);
        CommentCursor parsed = CommentCursor.decode(cursor);
        OffsetDateTime afterCreatedAt = parsed == null ? CURSOR_EPOCH : parsed.createdAt();
        UUID afterId = parsed == null ? CURSOR_MIN_UUID : parsed.id();

        Pageable pageable = PageRequest.of(0, limit + 1);
        List<Comment> result = commentRepository.findRepliesAfter(rootCommentId, afterCreatedAt, afterId, pageable);

        boolean hasMore = result.size() > limit;
        if (hasMore) {
            result = result.subList(0, limit);
        }

        Set<UUID> authorIds = result.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(authorIds);

        List<CommentResponse> items = result.stream()
                .map(c -> commentMapper.toResponse(c, null, profiles.get(c.getAuthorId())))
                .toList();

        String nextCursor = null;
        if (hasMore && !result.isEmpty()) {
            Comment last = result.get(result.size() - 1);
            nextCursor = CommentCursor.encode(last.getCreatedAt(), last.getId());
        }

        return new CommentPageResponse(items, nextCursor);
    }

    @Transactional
    public CommentResponse updateComment(UUID commentId, CommentUpdateRequest request, UUID currentUserId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Comment not found: " + commentId));

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new InvalidCommentStateException("Cannot edit a deleted comment");
        }

        if (!comment.getAuthorId().equals(currentUserId)) {
            throw new NotCommentAuthorException("You are not the author of this comment");
        }

        if (comment.getCreatedAt().plus(EDIT_WINDOW).isBefore(OffsetDateTime.now())) {
            throw new CommentEditWindowExpiredException("Edit window of 15 minutes has expired");
        }

        comment.setContent(request.content());
        comment = commentRepository.save(comment);

        Long repliesCount = comment.getParentId() == null
                ? commentRepository.countByParentId(comment.getId())
                : null;
        
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(Set.of(comment.getAuthorId()));
        return commentMapper.toResponse(comment, repliesCount, profiles.get(comment.getAuthorId()));
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID currentUserId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Comment not found: " + commentId));

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new InvalidCommentStateException("Comment is already deleted");
        }

        if (!comment.getAuthorId().equals(currentUserId)) {
            throw new NotCommentAuthorException("You are not the author of this comment");
        }

        comment.setIsDeleted(true);
        commentRepository.save(comment);

        counterService.decrementComments(comment.getPostId());
    }

    private int clampPageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
