package com.diplom.contentservice.service;

import com.diplom.contentservice.dto.UpvoteResponse;
import com.diplom.contentservice.entity.Post;
import com.diplom.contentservice.entity.PostStatus;
import com.diplom.contentservice.entity.PostUpvote;
import com.diplom.contentservice.entity.PostUpvoteId;
import com.diplom.contentservice.exception.InvalidPostStateException;
import com.diplom.contentservice.exception.PostNotFoundException;
import com.diplom.contentservice.exception.SelfUpvoteException;
import com.diplom.contentservice.repository.PostRepository;
import com.diplom.contentservice.repository.PostUpvoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostUpvoteService {

    private final PostRepository postRepository;
    private final PostUpvoteRepository upvoteRepository;
    private final CounterService counterService;

    @Transactional
    public UpvoteResponse toggleUpvote(UUID postId, UUID currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post " + postId + " not found"));

        if (post.getStatusId() != PostStatus.PUBLISHED.getId()) {
            throw new InvalidPostStateException("Cannot upvote a post that is not published");
        }

        if (post.getAuthorId().equals(currentUserId)) {
            throw new SelfUpvoteException("Cannot upvote your own post");
        }

        PostUpvoteId id = new PostUpvoteId(postId, currentUserId);
        boolean wasUpvoted = upvoteRepository.existsById(id);

        if (wasUpvoted) {
            upvoteRepository.deleteById(id);
            counterService.decrementUpvotes(postId);
        } else {
            upvoteRepository.save(PostUpvote.builder()
                    .postId(postId)
                    .userId(currentUserId)
                    .build());
            counterService.incrementUpvotes(postId);
        }

        long mergedUpvotes = post.getUpvotesCount() + counterService.getDeltas(postId).upvotes();
        return new UpvoteResponse(!wasUpvoted, (int) Math.max(0, mergedUpvotes));
    }
}
