package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.CreatePairedRoomRequest;
import com.diplom.chatservice.dto.CreateSoloRoomRequest;
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
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.NotFriendsException;
import com.diplom.chatservice.exception.NotRoomParticipantException;
import com.diplom.chatservice.exception.RoomFullException;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.outbox.OutboxEventFactory;
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

    @Value("${chat.default-ai-model}")
    private String defaultAiModel;

    // ==================== CREATE PAIRED ====================

    @Transactional
    public RoomResponse createPairedRoom(CreatePairedRoomRequest request, UUID callerId) {
        if (request.inviteMode() == InviteMode.LINK) {
            throw new IllegalArgumentException("LINK invite mode is not supported in this phase");
        }
        if (request.friendUserId() == null) {
            throw new IllegalArgumentException("friendUserId is required for FRIEND invite mode");
        }
        if (request.friendUserId().equals(callerId)) {
            throw new IllegalArgumentException("Cannot invite yourself to a paired room");
        }

        // Verify friendship using LEAST/GREATEST ordering
        UUID userA = callerId.compareTo(request.friendUserId()) < 0 ? callerId : request.friendUserId();
        UUID userB = callerId.compareTo(request.friendUserId()) < 0 ? request.friendUserId() : callerId;
        FriendLinkId friendLinkId = new FriendLinkId(userA, userB);

        if (!friendLinkRepository.existsById(friendLinkId)) {
            throw new NotFriendsException("You are not friends with the invited user");
        }

        // Create room
        Room room = Room.builder()
            .typeId(ROOM_TYPE_PAIRED)
            .statusId(STATUS_CREATED)
            .ownerUserId(callerId)
            .aiModel(defaultAiModel)
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
            .joinedAt(OffsetDateTime.now())
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
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

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

    // ==================== LIST ROOMS ====================

    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> listRooms(UUID callerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Room> roomPage = roomRepository.findRoomsByParticipantUserId(callerId, pageable);

        return roomPage.getContent().stream()
            .map(room -> {
                RoomParticipant myParticipant = participantRepository.findByRoomIdAndUserId(room.getId(), callerId)
                    .orElseThrow(() -> new IllegalStateException("Participant not found"));
                return roomMapper.toRoomSummaryResponse(room, myParticipant);
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
}
