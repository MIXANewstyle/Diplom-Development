package com.diplom.userservice.service;

import com.diplom.userservice.entity.AuthorFollow;
import com.diplom.userservice.entity.AuthorFollowId;
import com.diplom.userservice.entity.Friendship;
import com.diplom.userservice.entity.FriendshipId;
import com.diplom.userservice.entity.User;
import com.diplom.userservice.entity.UserRole;
import com.diplom.userservice.entity.FriendshipStatus;
import com.diplom.userservice.entity.UserOutboxEvent;
import com.diplom.userservice.event.EventType;
import com.diplom.userservice.event.AuthorFollowedEvent;
import com.diplom.userservice.event.AuthorUnfollowedEvent;
import com.diplom.userservice.event.FriendshipAcceptedEvent;
import com.diplom.userservice.exception.*;
import com.diplom.userservice.outbox.OutboxEventFactory;
import com.diplom.userservice.repository.UserRepository;
import com.diplom.userservice.repository.AuthorFollowRepository;
import com.diplom.userservice.repository.FriendshipRepository;
import com.diplom.userservice.repository.UserOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.diplom.userservice.dto.FollowedAuthorResponse;
import com.diplom.userservice.dto.MyFriendsResponse;
import com.diplom.userservice.dto.UserBatchResponse;
import com.diplom.userservice.entity.UserProfile;
import com.diplom.userservice.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
public class SocialService {

    private final AuthorFollowRepository authorFollowRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final UserOutboxEventRepository outboxEventRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public void followAuthor(UUID followerId, UUID authorId) {
        if (followerId.equals(authorId)) {
            throw new SelfActionException("Cannot follow yourself");
        }
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException("User " + authorId + " not found"));
        if (author.getRoleId() != UserRole.AUTHOR.getId()) {
            throw new AuthorRoleRequiredException("Target user is not an author");
        }
        if (authorFollowRepository.existsById(new AuthorFollowId(followerId, authorId))) {
            throw new AlreadyFollowingException("Already following this author");
        }
        AuthorFollow follow = AuthorFollow.builder()
                .followerId(followerId)
                .authorId(authorId)
                .build();
        authorFollowRepository.save(follow);

        UserOutboxEvent event = outboxEventFactory.create(
                EventType.FOLLOW_ADDED,
                new AuthorFollowedEvent(followerId, authorId, OffsetDateTime.now())
        );
        outboxEventRepository.save(event);
    }

    @Transactional
    public void unfollowAuthor(UUID followerId, UUID authorId) {
        if (followerId.equals(authorId)) {
            throw new SelfActionException("Cannot unfollow yourself");
        }
        if (!authorFollowRepository.existsByFollowerIdAndAuthorId(followerId, authorId)) {
            throw new NotFollowingException("Not following this author");
        }
        authorFollowRepository.deleteByFollowerIdAndAuthorId(followerId, authorId);

        UserOutboxEvent event = outboxEventFactory.create(
                EventType.FOLLOW_REMOVED,
                new AuthorUnfollowedEvent(followerId, authorId, OffsetDateTime.now())
        );
        outboxEventRepository.save(event);
    }

    @Transactional
    public void sendFriendRequest(UUID requesterId, UUID addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new SelfActionException("Cannot send friend request to yourself");
        }
        if (friendshipRepository.existsById(new FriendshipId(requesterId, addresseeId))) {
            throw new FriendshipAlreadyExistsException("Friend request already exists");
        }
        Friendship friendship = Friendship.builder()
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .statusId(FriendshipStatus.PENDING.getId())
                .build();
        friendshipRepository.save(friendship);
    }

    @Transactional
    public void acceptFriendRequest(UUID currentUserId, UUID requesterId) {
        Friendship friendship = friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, currentUserId)
                .orElseThrow(() -> new FriendshipNotFoundException("Friend request not found"));
        
        if (!friendship.getAddresseeId().equals(currentUserId)) {
            throw new UnauthorizedFriendshipActionException("Only the addressee can accept");
        }
        if (friendship.getStatusId() != FriendshipStatus.PENDING.getId()) {
            throw new InvalidFriendshipStateException("Friendship is not in PENDING state");
        }

        friendship.setStatusId(FriendshipStatus.ACCEPTED.getId());
        friendshipRepository.save(friendship);

        UserOutboxEvent event = outboxEventFactory.create(
                EventType.FRIENDSHIP_ACCEPTED,
                new FriendshipAcceptedEvent(requesterId, currentUserId, OffsetDateTime.now())
        );
        outboxEventRepository.save(event);
    }

    @Transactional
    public void declineFriendRequest(UUID currentUserId, UUID requesterId) {
        Friendship friendship = friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, currentUserId)
                .orElseThrow(() -> new FriendshipNotFoundException("Friend request not found"));

        if (!friendship.getAddresseeId().equals(currentUserId)) {
            throw new UnauthorizedFriendshipActionException("Only the addressee can decline");
        }
        if (friendship.getStatusId() != FriendshipStatus.PENDING.getId()) {
            throw new InvalidFriendshipStateException("Friendship is not in PENDING state");
        }

        friendship.setStatusId(FriendshipStatus.DECLINED.getId());
        friendshipRepository.save(friendship);
    }

    @Transactional
    public void cancelFriendRequest(UUID currentUserId, UUID addresseeId) {
        Friendship friendship = friendshipRepository.findByRequesterIdAndAddresseeId(currentUserId, addresseeId)
                .orElseThrow(() -> new FriendshipNotFoundException("Friend request not found"));

        if (friendship.getStatusId() != FriendshipStatus.PENDING.getId()) {
            throw new InvalidFriendshipStateException("Cannot cancel a friendship that is not pending");
        }

        friendshipRepository.deleteByRequesterIdAndAddresseeId(currentUserId, addresseeId);
    }

    @Transactional(readOnly = true)
    public List<FollowedAuthorResponse> getFollowedAuthors(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User " + userId + " not found");
        }
        
        List<AuthorFollow> follows = authorFollowRepository.findAllByFollowerId(userId);
        
        return follows.stream()
                .map(f -> new FollowedAuthorResponse(f.getAuthorId(), f.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MyFriendsResponse getMyFriends(UUID userId) {
        List<Friendship> friendships = friendshipRepository.findAllByRequesterIdOrAddresseeId(userId, userId);
        
        List<UUID> friendsIds = new ArrayList<>();
        List<UUID> incomingIds = new ArrayList<>();
        List<UUID> outgoingIds = new ArrayList<>();
        
        for (Friendship f : friendships) {
            if (f.getStatusId() == FriendshipStatus.ACCEPTED.getId()) {
                friendsIds.add(f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId());
            } else if (f.getStatusId() == FriendshipStatus.PENDING.getId()) {
                if (f.getAddresseeId().equals(userId)) {
                    incomingIds.add(f.getRequesterId());
                } else {
                    outgoingIds.add(f.getAddresseeId());
                }
            }
        }
        
        Set<UUID> allIds = new HashSet<>();
        allIds.addAll(friendsIds);
        allIds.addAll(incomingIds);
        allIds.addAll(outgoingIds);
        
        Map<UUID, UserProfile> profiles = userProfileRepository.findAllByUserIdIn(allIds).stream()
                .collect(Collectors.toMap(UserProfile::getId, p -> p));
                
        List<UserBatchResponse> friends = friendsIds.stream()
                .filter(profiles::containsKey)
                .map(id -> new UserBatchResponse(id, profiles.get(id).getUsername(), profiles.get(id).getFullName(), profiles.get(id).getAvatarUrl()))
                .toList();
                
        List<UserBatchResponse> incoming = incomingIds.stream()
                .filter(profiles::containsKey)
                .map(id -> new UserBatchResponse(id, profiles.get(id).getUsername(), profiles.get(id).getFullName(), profiles.get(id).getAvatarUrl()))
                .toList();
                
        List<UserBatchResponse> outgoing = outgoingIds.stream()
                .filter(profiles::containsKey)
                .map(id -> new UserBatchResponse(id, profiles.get(id).getUsername(), profiles.get(id).getFullName(), profiles.get(id).getAvatarUrl()))
                .toList();
                
        return new MyFriendsResponse(friends, incoming, outgoing);
    }
}
