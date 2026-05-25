package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.AdminCommentUpdateRequest;
import com.diplom.contentservice.dto.CommentResponse;
import com.diplom.contentservice.dto.UserBatchResponse;
import com.diplom.contentservice.entity.Comment;
import com.diplom.contentservice.entity.ModerationAction;
import com.diplom.contentservice.entity.ModerationTargetType;
import com.diplom.contentservice.event.CommentModeratedEvent;
import com.diplom.contentservice.event.EventType;
import com.diplom.contentservice.exception.CommentNotFoundException;
import com.diplom.contentservice.exception.InvalidCommentStateException;
import com.diplom.contentservice.outbox.OutboxEventFactory;
import com.diplom.contentservice.repository.CommentRepository;
import com.diplom.contentservice.repository.ContentOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCommentService {

    private final CommentRepository commentRepository;
    private final ContentOutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final ModerationLogService moderationLogService;
    private final CommentMapper commentMapper;
    private final ProfileCacheService profileCacheService;

    @Transactional
    public void moderateComment(UUID commentId, UUID adminId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommentNotFoundException("Comment " + commentId + " not found"));

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new InvalidCommentStateException("Comment is already deleted");
        }

        Map<String, Object> before = snapshotComment(comment);

        comment.setIsDeleted(true);
        commentRepository.save(comment);

        moderationLogService.log(
            adminId, ModerationAction.COMMENT_DELETED, ModerationTargetType.COMMENT,
            commentId, before, null);

        outboxEventRepository.save(outboxEventFactory.create(
            EventType.COMMENT_MODERATED,
            new CommentModeratedEvent(commentId, comment.getPostId(), adminId,
                ModerationAction.COMMENT_DELETED.name(), OffsetDateTime.now())
        ));
    }

    @Transactional
    public CommentResponse editComment(UUID commentId, AdminCommentUpdateRequest request, UUID adminId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CommentNotFoundException("Comment " + commentId + " not found"));

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new InvalidCommentStateException("Cannot edit a deleted comment");
        }

        Map<String, Object> before = snapshotComment(comment);
        comment.setContent(request.content());
        commentRepository.save(comment);
        Map<String, Object> after = snapshotComment(comment);

        moderationLogService.log(
            adminId, ModerationAction.COMMENT_EDITED, ModerationTargetType.COMMENT,
            commentId, before, after);

        outboxEventRepository.save(outboxEventFactory.create(
            EventType.COMMENT_MODERATED,
            new CommentModeratedEvent(commentId, comment.getPostId(), adminId,
                ModerationAction.COMMENT_EDITED.name(), OffsetDateTime.now())
        ));

        UserBatchResponse profile = profileCacheService
            .getProfiles(Set.of(comment.getAuthorId()))
            .get(comment.getAuthorId());
        Long repliesCount = comment.getParentId() == null
            ? commentRepository.countByParentId(comment.getId()) : null;
        return commentMapper.toResponse(comment, repliesCount, profile);
    }

    private Map<String, Object> snapshotComment(Comment c) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("content", c.getContent());
        snap.put("isDeleted", c.getIsDeleted());
        snap.put("authorId", c.getAuthorId().toString());
        snap.put("postId", c.getPostId().toString());
        return snap;
    }
}
