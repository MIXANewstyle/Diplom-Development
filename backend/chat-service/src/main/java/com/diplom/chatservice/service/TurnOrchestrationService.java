package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.dto.SubmitTurnRequest;
import com.diplom.chatservice.dto.SubmitTurnResponse;
import com.diplom.chatservice.dto.TurnResponse;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.LlmUnavailableException;
import com.diplom.chatservice.exception.NotRoomParticipantException;
import com.diplom.chatservice.exception.NotYourTurnException;
import com.diplom.chatservice.exception.RateLimitExceededException;
import com.diplom.chatservice.llm.ConversationAssembler;
import com.diplom.chatservice.llm.LlmClient;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.llm.LlmResponse;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurnOrchestrationService {

    private final TurnPersistenceService turnPersistenceService;
    private final ConversationAssembler conversationAssembler;
    private final LlmClient llmClient;
    private final TurnRepository turnRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final ChatLlmProperties llmProperties;

    public SubmitTurnResponse submitTurn(UUID roomId, UUID currentUserId, SubmitTurnRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        RoomParticipant callerParticipant = validateAndGetParticipant(roomId, currentUserId);

        validateSubmitPreconditions(room, callerParticipant);

        // Transaction 1
        Turn userTurn = turnPersistenceService.persistUserTurn(roomId, callerParticipant.getId(), request.text());

        return executeAiPipeline(roomId, userTurn);
    }

    public SubmitTurnResponse retryTurn(UUID roomId, UUID currentUserId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        RoomParticipant callerParticipant = validateAndGetParticipant(roomId, currentUserId);
        validateSubmitPreconditions(room, callerParticipant);

        List<Turn> allTurns = loadHistory(roomId);
        if (allTurns.isEmpty() || allTurns.get(allTurns.size() - 1).getRoleId() != 1) { // 1=USER
            throw new InvalidRoomStateException("No pending AI response to retry");
        }

        // Transaction 1r
        turnPersistenceService.setAiProcessing(roomId);

        return executeAiPipeline(roomId, null);
    }

    private SubmitTurnResponse executeAiPipeline(UUID roomId, Turn userTurn) {
        Room updatedRoom = roomRepository.findById(roomId).orElseThrow();
        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        List<Turn> allTurns = loadHistory(roomId);

        LlmRequest llmRequest = conversationAssembler.assemble(updatedRoom, participants, allTurns);

        LlmResponse llmResponse;
        try {
            llmResponse = llmClient.complete(llmRequest);
        } catch (LlmUnavailableException e) {
            turnPersistenceService.handleAiFailure(roomId);
            throw e;
        }

        // Transaction 2s
        Turn assistantTurn = turnPersistenceService.persistAssistantTurn(roomId, llmResponse.content(), llmResponse.promptTokens(), llmResponse.completionTokens());

        return new SubmitTurnResponse(mapToResponse(userTurn), mapToResponse(assistantTurn));
    }

    private RoomParticipant validateAndGetParticipant(UUID roomId, UUID currentUserId) {
        return participantRepository.findByRoomIdAndUserId(roomId, currentUserId)
                .orElseThrow(() -> new NotRoomParticipantException("Caller is not a participant of this room"));
    }

    private void validateSubmitPreconditions(Room room, RoomParticipant callerParticipant) {
        if (room.getStatusId() != 3) { // 3=ACTIVE
            throw new InvalidRoomStateException("Room must be ACTIVE");
        }
        if (!"A_COMPOSING".equals(room.getPhase())) {
            throw new InvalidRoomStateException("AI response in progress or room not ready");
        }
        if (!callerParticipant.getId().equals(room.getCurrentFloorParticipantId()) && room.getTypeId() == 1) { // 1=PAIRED
            throw new NotYourTurnException("It is not your turn to submit");
        }

        long turnCount = turnRepository.findByRoomIdOrderBySeqAsc(room.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
        if (turnCount >= llmProperties.hardTurnCap()) {
            throw new RateLimitExceededException("Hard turn cap reached");
        }
    }

    private List<Turn> loadHistory(UUID roomId) {
        return turnRepository.findByRoomIdOrderBySeqAsc(roomId, org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    private TurnResponse mapToResponse(Turn turn) {
        if (turn == null) return null;
        String roleStr = turn.getRoleId() == 1 ? "USER" : (turn.getRoleId() == 2 ? "ASSISTANT" : "SYSTEM");
        return new TurnResponse(
                turn.getId(),
                turn.getRoomId(),
                turn.getSeq(),
                roleStr,
                turn.getParticipantId(),
                turn.getContent(),
                turn.getPromptTokens(),
                turn.getCompletionTokens(),
                turn.getCreatedAt()
        );
    }
}
