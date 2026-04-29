package com.diplom.userservice.service;

import com.diplom.userservice.entity.AuthorFollow;
import com.diplom.userservice.entity.AuthorFollowId;
import com.diplom.userservice.entity.Friendship;
import com.diplom.userservice.entity.FriendshipId;
import com.diplom.userservice.entity.User;
import com.diplom.userservice.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Transactional
    public void followAuthor(UUID followerId, UUID authorId) {
        if (followerId.equals(authorId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("Author not found"));
        if (author.getRoleId() != 4) {
            throw new IllegalArgumentException("You can only follow users with the AUTHOR role");
        }
        if (authorFollowRepository.existsById(new AuthorFollowId(followerId, authorId))) {
            throw new IllegalArgumentException("Already following this author");
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
        if (friendshipRepository.existsById(new FriendshipId(requesterId, addresseeId))) {
            throw new IllegalArgumentException("Friend request already exists");
        }
        Friendship friendship = Friendship.builder()
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .statusId(1) // 1=PENDING
                .build();
        friendshipRepository.save(friendship);
    }
}
