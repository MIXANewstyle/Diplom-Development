package com.diplom.chatservice.service;

import com.diplom.chatservice.entity.FriendLink;
import com.diplom.chatservice.entity.FriendLinkId;
import com.diplom.chatservice.repository.FriendLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendLinkProjectionService {

    private final FriendLinkRepository friendLinkRepository;

    @Transactional
    public void upsertFriendship(UUID user1, UUID user2) {
        UUID userA = user1.compareTo(user2) <= 0 ? user1 : user2;
        UUID userB = user1.compareTo(user2) <= 0 ? user2 : user1;

        if (friendLinkRepository.existsById(new FriendLinkId(userA, userB))) {
            return;
        }

        friendLinkRepository.save(FriendLink.builder()
            .userA(userA)
            .userB(userB)
            .createdAt(OffsetDateTime.now())
            .build());
    }
}
