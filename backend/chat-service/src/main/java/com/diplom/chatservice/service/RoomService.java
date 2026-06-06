package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.CreatePairedRoomRequest;
import com.diplom.chatservice.dto.CreateSoloRoomRequest;
import com.diplom.chatservice.dto.EndDecision;
import com.diplom.chatservice.dto.EndRespondRequest;
import com.diplom.chatservice.dto.InviteMode;
import com.diplom.chatservice.dto.RoomResponse;
import com.diplom.chatservice.dto.RoomSummaryResponse;
import com.diplom.chatservice.dto.TurnResponse;
import com.diplom.chatservice.dto.TurnsPageResponse;
import com.diplom.chatservice.entity.ChatOutboxEvent;
import com.diplom.chatservice.entity.FriendLinkId;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.event.EventType;
import com.diplom.chatservice.event.PairInviteSentEvent;
import com.diplom.chatservice.event.RoomArchivedEvent;
import com.diplom.chatservice.exception.InvalidSeedContextException;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.NotFriendsException;
import com.diplom.chatservice.exception.NotRoomParticipantException;
import com.diplom.chatservice.exception.RoomFullException;
import com.diplom.chatservice.exception.SubscriptionRequiredException;
import com.diplom.chatservice.exception.UserModeratedException;
import com.diplom.chatservice.outbox.OutboxEventFactory;
import com.diplom.chatservice.event.RoomArchivedInternalEvent;
import com.diplom.chatservice.dto.ws.DialogueAbandonedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.diplom.chatservice.repository.ChatOutboxEventRepository;
import com.diplom.chatservice.repository.FriendLinkRepository;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private static final int ROOM_TYPE_PAIRED = 1;
    private static final int ROOM_TYPE_SOLO = 2;

    private static final int STATUS_CREATED = 1;
    private static final int STATUS_WAITING_CONSENT = 2;
    private static final int STATUS_ACTIVE = 3;
    private static final int STATUS_ENDING = 4;
    private static final int STATUS_ARCHIVED = 5;
    private static final int STATUS_ABANDONED = 6;
    private static final int STATUS_EXPIRED = 7;

    private static final int ROLE_INITIATOR = 1;
    private static final int ROLE_INVITEE = 2;
    private static final int ROLE_SOLO = 3;

    private static final int SOLO_MODE_PROBLEM_SOLVING = 1;

    private static final String PHASE_A_COMPOSING = "A_COMPOSING";

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final FriendLinkRepository friendLinkRepository;
    private final TurnRepository turnRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final ChatOutboxEventRepository outboxEventRepository;
    private final RoomMapper roomMapper;
    private final ModerationBlocklistService moderationBlocklistService;
    private final RoleCacheService roleCacheService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;


    @Value("${chat.default-ai-model}")
    private String defaultAiModel;

    private void checkNotTerminal(Room room) {
        int status = room.getStatusId();
        if (status == STATUS_ARCHIVED || status == STATUS_ABANDONED || status == STATUS_EXPIRED) {
            throw new InvalidRoomStateException("Room is in a terminal state and cannot be modified");
        }
    }

    // ==================== CREATE PAIRED ====================

    @Transactional
    public RoomResponse createPairedRoom(CreatePairedRoomRequest request, UUID callerId) {
        checkPassiveGates(callerId, true);
        if (request.inviteMode() == InviteMode.LINK) {
            throw new IllegalArgumentException("LINK invite mode is not supported in this phase");
        }
        if (request.friendUserId() == null) {
            throw new IllegalArgumentException("friendUserId is required for FRIEND invite mode");
        }
        if (request.friendUserId().equals(callerId)) {
            throw new IllegalArgumentException("Cannot invite yourself to a paired room");
        }

        // Verify friendship by checking both permutations to avoid UUID sort discrepancies
        boolean isFriend = friendLinkRepository.existsById(new FriendLinkId(callerId, request.friendUserId())) ||
                           friendLinkRepository.existsById(new FriendLinkId(request.friendUserId(), callerId));

        if (!isFriend) {
            throw new NotFriendsException("You are not friends with the invited user");
        }

        UUID seedContextRoomId = request.seedContextRoomId();
        if (seedContextRoomId != null) {
            Room seedRoom = roomRepository.findById(seedContextRoomId)
                .orElseThrow(() -> new InvalidSeedContextException("Seed room must be your own archived dialogue"));
            
            if (seedRoom.getStatusId() != STATUS_ARCHIVED) {
                throw new InvalidSeedContextException("Seed room must be your own archived dialogue");
            }
            
            boolean wasParticipant = participantRepository.existsByRoomIdAndUserId(seedContextRoomId, callerId);
            if (!wasParticipant) {
                throw new InvalidSeedContextException("Seed room must be your own archived dialogue");
            }
        }

        // Create room
        Room room = Room.builder()
            .typeId(ROOM_TYPE_PAIRED)
            .statusId(STATUS_CREATED)
            .ownerUserId(callerId)
            .aiModel(defaultAiModel)
            .seedContextRoomId(seedContextRoomId)
            .build();
        room = roomRepository.save(room);

        // Create initiator participant
        RoomParticipant initiator = RoomParticipant.builder()
            .roomId(room.getId())
            .userId(callerId)
            .roleId(ROLE_INITIATOR)
            .joinedAt(OffsetDateTime.now())
            .build();
        initiator = participantRepository.save(initiator);

        // Pre-create invitee participant (joined_at is null until they actually join)
        RoomParticipant invitee = RoomParticipant.builder()
            .roomId(room.getId())
            .userId(request.friendUserId())
            .roleId(ROLE_INVITEE)
            .build();
        invitee = participantRepository.save(invitee);

        // Emit PAIR_INVITE_SENT event via outbox
        PairInviteSentEvent payload = new PairInviteSentEvent(
            OffsetDateTime.now(),
            room.getId(),
            callerId,
            request.friendUserId()
        );
        ChatOutboxEvent outboxEvent = outboxEventFactory.create(EventType.PAIR_INVITE_SENT, payload);
        outboxEventRepository.save(outboxEvent);

        List<RoomParticipant> participants = List.of(initiator, invitee);
        return roomMapper.toRoomResponse(room, participants);
    }

    // ==================== CREATE SOLO ====================

    @Transactional
    public RoomResponse createSoloRoom(CreateSoloRoomRequest request, UUID callerId) {
        checkPassiveGates(callerId, true);
        // Create room — solo goes straight to ACTIVE
        Room room = Room.builder()
            .typeId(ROOM_TYPE_SOLO)
            .soloModeId(SOLO_MODE_PROBLEM_SOLVING)
            .statusId(STATUS_ACTIVE)
            .ownerUserId(callerId)
            .aiModel(defaultAiModel)
            .phase(PHASE_A_COMPOSING)
            .startedAt(OffsetDateTime.now())
            .build();
        room = roomRepository.save(room);

        // Create the solo participant
        RoomParticipant participant = RoomParticipant.builder()
            .roomId(room.getId())
            .userId(callerId)
            .roleId(ROLE_SOLO)
            .joinedAt(OffsetDateTime.now())
            .build();
        participant = participantRepository.save(participant);

        // Set the floor holder
        room.setCurrentFloorParticipantId(participant.getId());
        room = roomRepository.save(room);

        return roomMapper.toRoomResponse(room, List.of(participant));
    }

    // ==================== JOIN ====================

    @Transactional
    public RoomResponse joinRoom(UUID roomId, UUID callerId) {
        checkPassiveGates(callerId, false);
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        checkNotTerminal(room);

        // Caller cannot join their own room as the second participant
        if (room.getOwnerUserId().equals(callerId)) {
            throw new IllegalArgumentException("Cannot join your own room as the second participant");
        }

        // Room must be in CREATED status
        if (room.getStatusId() != STATUS_CREATED) {
            throw new InvalidRoomStateException("Room is not in CREATED state");
        }

        // Find the invitee participant slot for this caller
        RoomParticipant inviteeSlot = participantRepository.findByRoomIdAndUserId(roomId, callerId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        if (inviteeSlot.getRoleId() != ROLE_INVITEE) {
            throw new NotRoomParticipantException("You are not an invitee of this room");
        }

        // Check if room already has two present participants (should not in CREATED state, but guard)
        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        if (participants.size() >= 2 && participants.stream().allMatch(p -> p.getJoinedAt() != null)) {
            throw new RoomFullException("Room already has two present participants");
        }

        // Set joined_at and transition to WAITING_CONSENT
        inviteeSlot.setJoinedAt(OffsetDateTime.now());
        participantRepository.save(inviteeSlot);

        room.setStatusId(STATUS_WAITING_CONSENT);
        room = roomRepository.save(room);

        return roomMapper.toRoomResponse(room, participantRepository.findByRoomId(roomId));
    }

    // ==================== CONSENT START ====================

    @Transactional
    public RoomResponse consentStart(UUID roomId, UUID callerId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        checkNotTerminal(room);

        if (room.getStatusId() != STATUS_WAITING_CONSENT) {
            throw new InvalidRoomStateException("Room is not in WAITING_CONSENT state");
        }

        RoomParticipant callerParticipant = participantRepository.findByRoomIdAndUserId(roomId, callerId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        // Set consent
        callerParticipant.setConsentStartAt(OffsetDateTime.now());
        participantRepository.save(callerParticipant);

        // Check if both participants have consented
        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        boolean allConsented = participants.stream()
            .allMatch(p -> p.getConsentStartAt() != null);

        if (allConsented) {
            // Find the initiator to assign first floor
            RoomParticipant initiator = participants.stream()
                .filter(p -> p.getRoleId() == ROLE_INITIATOR)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No initiator found in room " + roomId));

            room.setStatusId(STATUS_ACTIVE);
            room.setStartedAt(OffsetDateTime.now());
            room.setPhase(PHASE_A_COMPOSING);
            room.setCurrentFloorParticipantId(initiator.getId());
            room = roomRepository.save(room);
        }

        return roomMapper.toRoomResponse(room, participants);
    }

    // ==================== CONSENT REVOKE ====================

    @Transactional
    public RoomResponse consentRevoke(UUID roomId, UUID callerId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        checkNotTerminal(room);

        if (room.getStatusId() != STATUS_WAITING_CONSENT) {
            throw new InvalidRoomStateException("Room is not in WAITING_CONSENT state");
        }

        RoomParticipant callerParticipant = participantRepository.findByRoomIdAndUserId(roomId, callerId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        callerParticipant.setConsentStartAt(null);
        participantRepository.save(callerParticipant);

        return roomMapper.toRoomResponse(room, participantRepository.findByRoomId(roomId));
    }

    // ==================== END HANDSHAKE ====================

    @Transactional
    public RoomResponse endPropose(UUID roomId, UUID callerId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        checkNotTerminal(room);

        if (room.getTypeId() != ROOM_TYPE_PAIRED || room.getStatusId() != STATUS_ACTIVE) {
            throw new InvalidRoomStateException("Room must be PAIRED and ACTIVE to propose end");
        }

        RoomParticipant callerParticipant = participantRepository.findByRoomIdAndUserId(roomId, callerId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        room.setStatusId(STATUS_ENDING);
        room.setEndingProposedByParticipantId(callerParticipant.getId());
        room = roomRepository.save(room);

        return roomMapper.toRoomResponse(room, participantRepository.findByRoomId(roomId));
    }

    @Transactional
    public RoomResponse endRespond(UUID roomId, EndRespondRequest request, UUID callerId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        checkNotTerminal(room);

        if (room.getStatusId() != STATUS_ENDING) {
            throw new InvalidRoomStateException("Room is not in ENDING state");
        }

        RoomParticipant callerParticipant = participantRepository.findByRoomIdAndUserId(roomId, callerId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        if (callerParticipant.getId().equals(room.getEndingProposedByParticipantId())) {
            throw new InvalidRoomStateException("The participant who proposed ending cannot respond to it");
        }

        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);

        if (request.decision() == EndDecision.DECLINE) {
            room.setStatusId(STATUS_ACTIVE);
            room.setEndingProposedByParticipantId(null);
            room = roomRepository.save(room);
            return roomMapper.toRoomResponse(room, participants);
        } else {
            return archiveRoom(room, participants);
        }
    }

    @Transactional
    public RoomResponse endSolo(UUID roomId, UUID callerId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        checkNotTerminal(room);

        if (room.getTypeId() != ROOM_TYPE_SOLO || room.getStatusId() != STATUS_ACTIVE) {
            throw new InvalidRoomStateException("Room must be SOLO and ACTIVE to end");
        }

        if (!participantRepository.existsByRoomIdAndUserId(roomId, callerId)) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        }

        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        return archiveRoom(room, participants);
    }

    private RoomResponse archiveRoom(Room room, List<RoomParticipant> participants) {
        room.setStatusId(STATUS_ARCHIVED);
        room.setEndedAt(OffsetDateTime.now());
        room.setPhase(null);
        room = roomRepository.save(room);

        List<UUID> participantUserIds = participants.stream()
            .map(RoomParticipant::getUserId)
            .filter(userId -> userId != null)
            .toList();

        RoomArchivedEvent payload = new RoomArchivedEvent(
            OffsetDateTime.now(),
            room.getId(),
            room.getTypeId() == ROOM_TYPE_PAIRED ? "PAIRED" : "SOLO",
            participantUserIds,
            room.getEndedAt()
        );
        ChatOutboxEvent outboxEvent = outboxEventFactory.create(EventType.ROOM_ARCHIVED, payload);
        outboxEventRepository.save(outboxEvent);

        // Publish internal event to trigger async summarization (Phase 4c-2a)
        applicationEventPublisher.publishEvent(new RoomArchivedInternalEvent(room.getId(), room.getTypeId()));

        return roomMapper.toRoomResponse(room, participants);
    }

    // ==================== LIST ROOMS ====================

    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> listRooms(UUID callerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Room> roomPage = roomRepository.findRoomsByParticipantUserId(callerId, pageable);

        return roomPage.getContent().stream()
            .map(room -> {
                RoomParticipant myParticipant = participantRepository.findByRoomIdAndUserId(room.getId(), callerId)
                    .orElseThrow(() -> new IllegalStateException("Participant not found"));
                return roomMapper.toRoomSummaryResponse(room, myParticipant, null, null);
            })
            .toList();
    }

    /**
     * List rooms with identity enrichment for the other participant.
     * Called from the controller where the JWT is available.
     */
    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> listRoomsEnriched(UUID callerId, int page, int size,
                                                        String jwt,
                                                        ProfileCacheService profileCacheService,
                                                        RoomMapper mapper) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Room> roomPage = roomRepository.findRoomsByParticipantUserId(callerId, pageable);
        return enrichRoomPage(roomPage, callerId, jwt, profileCacheService, mapper);
    }

    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> listSeedEligibleRooms(UUID callerId, int page, int size,
                                                           String jwt,
                                                           ProfileCacheService profileCacheService,
                                                           RoomMapper mapper) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Room> roomPage = roomRepository.findSeedEligibleRooms(callerId, pageable);
        return enrichRoomPage(roomPage, callerId, jwt, profileCacheService, mapper);
    }

    private List<RoomSummaryResponse> enrichRoomPage(Page<Room> roomPage, UUID callerId,
                                                     String jwt,
                                                     ProfileCacheService profileCacheService,
                                                     RoomMapper mapper) {
        // Collect all other-participant user IDs across all rooms for a single batch lookup
        List<Room> rooms = roomPage.getContent();
        java.util.Map<UUID, List<RoomParticipant>> roomParticipantsMap = new java.util.HashMap<>();
        java.util.Set<UUID> otherUserIds = new java.util.HashSet<>();

        for (Room room : rooms) {
            List<RoomParticipant> participants = participantRepository.findByRoomId(room.getId());
            roomParticipantsMap.put(room.getId(), participants);
            participants.stream()
                .filter(p -> p.getUserId() != null && !p.getUserId().equals(callerId))
                .forEach(p -> otherUserIds.add(p.getUserId()));
        }

        // Batch-fetch all other-participant profiles at once
        java.util.Map<UUID, com.diplom.chatservice.dto.UserBatchResponse> profiles =
            profileCacheService.getProfiles(otherUserIds, jwt);

        return rooms.stream()
            .map(room -> {
                List<RoomParticipant> participants = roomParticipantsMap.get(room.getId());
                RoomParticipant myParticipant = participants.stream()
                    .filter(p -> callerId.equals(p.getUserId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Participant not found"));

                // Find the other participant (if any — solo rooms have none)
                String otherDisplayName = null;
                String otherAvatarUrl = null;
                for (RoomParticipant p : participants) {
                    if (p.getUserId() != null && !p.getUserId().equals(callerId)) {
                        com.diplom.chatservice.dto.UserBatchResponse profile = profiles.get(p.getUserId());
                        if (profile != null) {
                            otherDisplayName = profile.username();
                            otherAvatarUrl = profile.avatarUrl();
                        }
                        break;
                    }
                    // Guest other-participant: use guestDisplayName, avatar null
                    if (p.getUserId() == null && p.getGuestDisplayName() != null) {
                        otherDisplayName = p.getGuestDisplayName();
                        break;
                    }
                }

                return mapper.toRoomSummaryResponse(room, myParticipant,
                    otherDisplayName, otherAvatarUrl);
            })
            .toList();
    }


    // ==================== GET ROOM ====================

    @Transactional(readOnly = true)
    public RoomResponse getRoom(UUID roomId, UUID callerId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        // Verify caller is a participant — hide existence from non-participants
        if (!participantRepository.existsByRoomIdAndUserId(roomId, callerId)) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        }

        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        return roomMapper.toRoomResponse(room, participants);
    }

    // ==================== GET TURNS ====================

    @Transactional(readOnly = true)
    public TurnsPageResponse getTurns(UUID roomId, UUID callerId, int page, int size) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        if (!participantRepository.existsByRoomIdAndUserId(roomId, callerId)) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Turn> turnPage = turnRepository.findByRoomIdOrderBySeqAsc(roomId, pageable);

        List<TurnResponse> items = turnPage.getContent().stream()
            .map(roomMapper::toTurnResponse)
            .toList();

        return new TurnsPageResponse(
            items,
            turnPage.getNumber(),
            turnPage.getSize(),
            turnPage.getTotalElements(),
            turnPage.getTotalPages()
        );
    }

    private void checkPassiveGates(UUID callerId, boolean checkRole) {
        if (moderationBlocklistService.isBlocked(callerId)) {
            throw new UserModeratedException();
        }
        if (checkRole) {
            String role = roleCacheService.getCachedRole(callerId);
            if (role != null && (role.equals("FREE") || role.equals("GUEST"))) {
                throw new SubscriptionRequiredException();
            }
        }
    }

    @Transactional
    public void abandonRoomsForBannedUser(UUID userId) {
        List<Room> rooms = roomRepository.findActiveOrEndingRoomsByParticipantUserId(userId);
        for (Room room : rooms) {
            room.setStatusId(STATUS_ABANDONED);
            room.setEndedAt(OffsetDateTime.now());
            room.setPhase(null);
            roomRepository.save(room);

            // Broadcast DIALOGUE_ABANDONED to the room topic
            messagingTemplate.convertAndSend(
                "/topic/rooms/" + room.getId(),
                new DialogueAbandonedEvent(room.getId(), "MODERATION")
            );
            log.info("Abandoned room {} due to user {} moderation", room.getId(), userId);
        }
    }
}
