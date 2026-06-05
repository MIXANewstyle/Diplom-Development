package com.diplom.chatservice.service;

import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurnPersistenceService {

    private final RoomRepository roomRepository;
    private final TurnRepository turnRepository;
    private final RoomParticipantRepository participantRepository;

    @Transactional
    public Turn persistUserTurn(UUID roomId, UUID participantId, String text) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        // Determine next seq
        Integer maxSeq = turnRepository.findByRoomIdOrderBySeqAsc(roomId, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .map(Turn::getSeq)
                .max(Integer::compareTo)
                .orElse(0);

        Turn turn = Turn.builder()
                .roomId(roomId)
                .participantId(participantId)
                .roleId(1) // 1=USER
                .seq(maxSeq + 1)
                .content(text)
                .createdAt(OffsetDateTime.now())
                .build();
        turn = turnRepository.save(turn);

        room.setPhase("AI_PROCESSING");
        roomRepository.save(room); // Bumps @Version

        return turn;
    }

    @Transactional
    public Turn persistAssistantTurn(UUID roomId, String content, Integer promptTokens, Integer completionTokens) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (room.getStatusId() != 3 || !"AI_PROCESSING".equals(room.getPhase())) { // 3=ACTIVE
            throw new InvalidRoomStateException("Room must be ACTIVE and AI_PROCESSING");
        }

        Integer maxSeq = turnRepository.findByRoomIdOrderBySeqAsc(roomId, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .map(Turn::getSeq)
                .max(Integer::compareTo)
                .orElse(0);

        Turn turn = Turn.builder()
                .roomId(roomId)
                .participantId(null)
                .roleId(2) // 2=ASSISTANT
                .seq(maxSeq + 1)
                .content(content)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .createdAt(OffsetDateTime.now())
                .build();
        turn = turnRepository.save(turn);

        // Flip floor if PAIRED
        if (room.getTypeId() == 1) { // 1=PAIRED
            UUID currentHolderId = room.getCurrentFloorParticipantId();
            UUID otherHolderId = participantRepository.findByRoomId(roomId).stream()
                    .map(RoomParticipant::getId)
                    .filter(id -> !id.equals(currentHolderId))
                    .findFirst()
                    .orElse(currentHolderId);
            room.setCurrentFloorParticipantId(otherHolderId);
        }

        room.setPhase("A_COMPOSING");
        roomRepository.save(room);

        return turn;
    }

    @Transactional
    public void handleAiFailure(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        room.setPhase("A_COMPOSING");
        roomRepository.save(room);
    }

    @Transactional
    public void setAiProcessing(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        room.setPhase("AI_PROCESSING");
        roomRepository.save(room);
    }
}
