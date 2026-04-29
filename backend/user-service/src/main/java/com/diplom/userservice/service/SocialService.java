package com.diplom.userservice.service;

import com.diplom.userservice.entity.AuthorFollow;
import com.diplom.userservice.entity.Friendship;
import com.diplom.userservice.repository.AuthorFollowRepository;
import com.diplom.userservice.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialService {

    private final AuthorFollowRepository authorFollowRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional
    public void followAuthor(UUID followerId, UUID authorId) {
        if (followerId.equals(authorId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }
        AuthorFollow follow = AuthorFollow.builder()
                .followerId(followerId)
                .authorId(authorId)
                .build();
        authorFollowRepository.save(follow);
    }

    @Transactional
    public void sendFriendRequest(UUID requesterId, UUID addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("Cannot send friend request to yourself");
        }
        Friendship friendship = Friendship.builder()
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .statusId(1) // 1=PENDING
                .build();
        friendshipRepository.save(friendship);
    }
}
