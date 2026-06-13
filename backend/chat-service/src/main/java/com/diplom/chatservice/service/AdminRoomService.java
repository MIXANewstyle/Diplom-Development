package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.admin.AdminRoomInspectionView;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.repository.InviteRepository;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import com.diplom.chatservice.dto.ws.DialogueArchivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRoomService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final TurnRepository turnRepository;
    private final InviteRepository inviteRepository;
    private final RoomBroadcaster roomBroadcaster;

    private static final int STATUS_ARCHIVED = 5;
    private static final int STATUS_ABANDONED = 6;
    private static final int STATUS_EXPIRED = 7;

    private static final int INVITE_STATUS_ACTIVE = 1;
    private static final int INVITE_STATUS_REVOKED = 2;

    @Transactional(readOnly = true)
    public AdminRoomInspectionView getInspectionView(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        List<Turn> transcript = turnRepository.findByRoomIdOrderBySeqAsc(roomId);

        return mapToInspectionView(room, participants, transcript);
    }

    @Transactional
    public AdminRoomInspectionView terminateRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        int status = room.getStatusId();
        if (status == STATUS_ARCHIVED || status == STATUS_ABANDONED || status == STATUS_EXPIRED) {
            throw new InvalidRoomStateException("Room is already in a terminal state");
        }

        // Force-archive
        room.setStatusId(STATUS_ARCHIVED);
        room.setEndedAt(OffsetDateTime.now());
        room = roomRepository.save(room);

        // Revoke active invite if any
        inviteRepository.updateStatusByRoomId(roomId, INVITE_STATUS_ACTIVE, INVITE_STATUS_REVOKED);

        // Broadcast DIALOGUE_ARCHIVED
        roomBroadcaster.broadcast(
                roomId,
                new DialogueArchivedEvent("DIALOGUE_ARCHIVED", roomId, room.getEndedAt(), "ADMIN")
        );

        // TODO: Enqueue summary job (Phase 4c-2a). Skipped intentionally for admin termination.

        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        List<Turn> transcript = turnRepository.findByRoomIdOrderBySeqAsc(roomId);

        return mapToInspectionView(room, participants, transcript);
    }

    private AdminRoomInspectionView mapToInspectionView(Room room, List<RoomParticipant> participants, List<Turn> transcript) {
        AdminRoomInspectionView.RoomInfo roomInfo = new AdminRoomInspectionView.RoomInfo(
                room.getId(),
                room.getTypeId(),
                room.getStatusId(),
                room.getPhase(),
                room.getCurrentFloorParticipantId(),
                room.getOwnerUserId(),
                room.getAiModel(),
                room.getSeedContextRoomId(),
                room.getCreatedAt(),
                room.getStartedAt(),
                room.getEndedAt(),
                room.getVersion()
        );

        List<AdminRoomInspectionView.ParticipantInfo> participantInfos = participants.stream()
                .map(p -> new AdminRoomInspectionView.ParticipantInfo(
                        p.getRoleId(),
                        p.getUserId(),
                        p.getGuestDisplayName(),
                        p.getGuestGenderId(),
                        p.getGuestAge(),
                        p.getConsentStartAt(),
                        p.getJoinedAt(),
                        p.getLastSeenAt()
                ))
                .toList();

        List<AdminRoomInspectionView.TurnInfo> turnInfos = transcript.stream()
                .map(t -> new AdminRoomInspectionView.TurnInfo(
                        t.getSeq(),
                        t.getRoleId(),
                        t.getParticipantId(),
                        t.getContent(),
                        t.getPromptTokens(),
                        t.getCompletionTokens(),
                        t.getCreatedAt()
                ))
                .toList();

        return new AdminRoomInspectionView(roomInfo, participantInfos, turnInfos);
    }
}
