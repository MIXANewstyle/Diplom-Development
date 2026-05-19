package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.CommentResponse;
import com.diplom.contentservice.entity.Comment;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {

    public CommentResponse toResponse(Comment c, Long repliesCount) {
        String content = c.getIsDeleted() ? "[comment deleted]" : c.getContent();
        return new CommentResponse(
            c.getId(),
            c.getPostId(),
            c.getAuthorId(),
            c.getParentId(),
            content,
            c.getIsDeleted(),
            c.getCreatedAt(),
            c.getUpdatedAt(),
            repliesCount
        );
    }
}
