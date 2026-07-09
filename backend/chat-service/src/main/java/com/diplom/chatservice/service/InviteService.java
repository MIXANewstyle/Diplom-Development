package com.diplom.chatservice.service;

import com.diplom.chatservice.client.InternalUserBatchClient;
import com.diplom.chatservice.config.ChatGuestProperties;
import com.diplom.chatservice.config.ChatInviteProperties;
import com.diplom.chatservice.dto.UserBatchResponse;
import com.diplom.chatservice.dto.invite.GuestJoinRequest;
import com.diplom.chatservice.dto.invite.InviteLandingResponse;
import com.diplom.chatservice.dto.invite.JoinInviteResponse;
import com.diplom.chatservice.dto.invite.MintInviteResponse;
import com.diplom.chatservice.entity.Invite;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.InviteInvalidException;
import com.diplom.chatservice.exception.NotRoomParticipantException;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.repository.InviteRepository;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.diplom.chatservice.ws.RoomBroadcaster;
import com.diplom.chatservice.dto.ws.ParticipantJoinedEvent;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteRepository inviteRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomBroadcaster roomBroadcaster;
    private final ChatInviteProperties inviteProperties;
    private final ChatGuestProperties guestProperties;
    private final JwtService jwtService;
    private final ProfileCacheService profileCacheService;
    private final ContextSnapshotService contextSnapshotService;
    private final InternalUserBatchClient internalUserBatchClient;
    private final SecureRandom secureRandom = new SecureRandom();


    private static final int INVITE_STATUS_ACTIVE = 1;
    private static final int INVITE_STATUS_REVOKED = 2;
    private static final int INVITE_STATUS_REDEEMED = 3;

    private static final int ROOM_STATUS_CREATED = 1;
    private static final int ROOM_STATUS_WAITING_CONSENT = 2;

    private static final int ROLE_INITIATOR = 1;
    private static final int ROLE_INVITEE = 2;

    @Transactional
    public MintInviteResponse mintInvite(UUID roomId, UUID callerId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));

        if (!participantRepository.existsByRoomIdAndUserId(roomId, callerId)) {
            throw new NotRoomParticipantException("Caller is not a participant");
        }

        if (room.getTypeId() != 1 || (room.getStatusId() != 1 && room.getStatusId() != 2)) { // 1 = PAIRED; status 1=CREATED, 2=WAITING_CONSENT
            throw new InvalidRoomStateException("Room is not accepting invites or not paired");
        }

        // Generate token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String hashedToken = hashToken(rawToken);

        // Revoke existing active invites
        inviteRepository.updateStatusByRoomId(roomId, INVITE_STATUS_ACTIVE, INVITE_STATUS_REVOKED);

        OffsetDateTime expiresAt = OffsetDateTime.now().plus(inviteProperties.ttl());

        Invite invite = Invite.builder()
                .roomId(roomId)
                .token(hashedToken)
                .statusId(INVITE_STATUS_ACTIVE)
                .createdBy(callerId)
                .expiresAt(expiresAt)
                .createdAt(OffsetDateTime.now())
                .build();
        
        inviteRepository.save(invite);
        return new MintInviteResponse(rawToken, expiresAt);
    }

    @Transactional
    public void revokeInvite(UUID roomId, UUID callerId) {
        if (!participantRepository.existsByRoomIdAndUserId(roomId, callerId)) {
            throw new NotRoomParticipantException("Caller is not a participant");
        }
        inviteRepository.updateStatusByRoomId(roomId, INVITE_STATUS_ACTIVE, INVITE_STATUS_REVOKED);
    }

    @Transactional(readOnly = true)
    public InviteLandingResponse getLandingInfo(String rawToken) {
        Invite invite = getValidInvite(rawToken);
        Room room = roomRepository.findById(invite.getRoomId())
                .orElseThrow(() -> new InviteInvalidException("Room associated with invite not found"));

        if (room.getStatusId() != 1 && room.getStatusId() != 2) { // 1=CREATED, 2=WAITING_CONSENT
            throw new InviteInvalidException("Room is no longer accepting participants");
        }

        List<RoomParticipant> participants = participantRepository.findByRoomId(room.getId());
        RoomParticipant initiator = participants.stream()
                .filter(p -> p.getRoleId() == ROLE_INITIATOR)
                .findFirst()
                .orElse(null);

        String hostName = "Unknown Host";
        if (initiator != null && initiator.getUserId() != null) {
            try {
                List<UserBatchResponse> profiles = internalUserBatchClient.batchGetProfiles(
                        List.of(initiator.getUserId()));
                if (profiles != null) {
                    UserBatchResponse profile = profiles.stream()
                            .filter(p -> p.id().equals(initiator.getUserId()))
                            .findFirst()
                            .orElse(null);
                    if (profile != null) {
                        hostName = profile.getDisplayName();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch host profile for invite landing (userId={}): {}",
                        initiator.getUserId(), e.getMessage());
                // Graceful: keep hostName = "Unknown Host"
            }
        }

        return new InviteLandingResponse(room.getId(), hostName, "LINK", invite.getExpiresAt());
    }

    @Transactional
    public void joinRegistered(String rawToken, UUID userId) {
        Invite invite = getValidInvite(rawToken);
        Room room = roomRepository.findWithLockById(invite.getRoomId())
                .orElseThrow(() -> new InviteInvalidException("Room not found"));

        if (room.getStatusId() != 1 && room.getStatusId() != 2) { // CREATED or WAITING_CONSENT
            throw new InviteInvalidException("Room is no longer accepting participants");
        }

        if (participantRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new InvalidRoomStateException("You are already a participant in this room");
        }

        if (participantRepository.countByRoomId(room.getId()) >= 2) {
            throw new InviteInvalidException("Room is full");
        }

        RoomParticipant newParticipant = new RoomParticipant();
        newParticipant.setRoomId(room.getId());
        newParticipant.setUserId(userId);
        newParticipant.setRoleId(ROLE_INVITEE);
        participantRepository.save(newParticipant);

        // Mirror the FRIEND join flow: once the invitee is attached, move the room to WAITING_CONSENT
        if (room.getStatusId() == ROOM_STATUS_CREATED) {
            room.setStatusId(ROOM_STATUS_WAITING_CONSENT);
            roomRepository.save(room);
        }

        if (inviteRepository.redeem(invite.getId()) == 0) {
            throw new InviteInvalidException("Invite is no longer active");
        }

        ParticipantJoinedEvent event = ParticipantJoinedEvent.of(newParticipant.getId(), OffsetDateTime.now(), room.getStatusId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                roomBroadcaster.broadcast(room.getId(), event);
            }
        });
    }

    @Transactional
    public JoinInviteResponse joinGuest(String rawToken, GuestJoinRequest request) {
        Invite invite = getValidInvite(rawToken);
        Room room = roomRepository.findWithLockById(invite.getRoomId())
                .orElseThrow(() -> new InviteInvalidException("Room not found"));

        if (room.getStatusId() != 1 && room.getStatusId() != 2) { // CREATED or WAITING_CONSENT
            throw new InviteInvalidException("Room is no longer accepting participants");
        }

        if (participantRepository.countByRoomId(room.getId()) >= 2) {
            throw new InviteInvalidException("Room is full");
        }

        RoomParticipant guestParticipant = new RoomParticipant();
        guestParticipant.setRoomId(room.getId());
        guestParticipant.setUserId(null); // Guest
        guestParticipant.setRoleId(ROLE_INVITEE);
        guestParticipant.setGuestDisplayName(request.displayName());
        guestParticipant.setGuestAge(request.age());
        participantRepository.save(guestParticipant);

        String genderLabel = "не указан";
        if (request.gender() != null && !request.gender().isBlank()) {
            for (Map.Entry<Integer, String> entry : guestProperties.genderLabels().entrySet()) {
                if (entry.getValue().equalsIgnoreCase(request.gender())) {
                    genderLabel = entry.getValue();
                    break;
                }
            }
            if (genderLabel.equals("не указан")) {
                genderLabel = request.gender();
            }
        }
        
        contextSnapshotService.captureForGuest(guestParticipant, genderLabel);

        // Mirror the FRIEND join flow: once the invitee is attached, move the room to WAITING_CONSENT
        if (room.getStatusId() == ROOM_STATUS_CREATED) {
            room.setStatusId(ROOM_STATUS_WAITING_CONSENT);
            roomRepository.save(room);
        }

        if (inviteRepository.redeem(invite.getId()) == 0) {
            throw new InviteInvalidException("Invite is no longer active");
        }

        String token = jwtService.mintRoomScopedToken(guestParticipant.getId(), room.getId());

        ParticipantJoinedEvent event = ParticipantJoinedEvent.of(guestParticipant.getId(), OffsetDateTime.now(), room.getStatusId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                roomBroadcaster.broadcast(room.getId(), event);
            }
        });

        return new JoinInviteResponse(token);
    }

    private Invite getValidInvite(String rawToken) {
        String hashedToken = hashToken(rawToken);
        Invite invite = inviteRepository.findByToken(hashedToken)
                .orElseThrow(() -> new InviteInvalidException("Invalid invite token"));

        if (invite.getStatusId() != INVITE_STATUS_ACTIVE) {
            throw new InviteInvalidException("Invite is no longer active");
        }
        if (invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InviteInvalidException("Invite has expired");
        }
        return invite;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
