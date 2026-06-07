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
    private final SummarizationService summarizationService;
    private final RateLimitService rateLimitService;

    public SubmitTurnResponse submitTurn(UUID roomId, Object principal, SubmitTurnRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        RoomParticipant callerParticipant = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(principal, roomId, participantRepository);

        validateSubmitPreconditions(room, callerParticipant);
        
        // Phase 4d Rate Limit Checks
        if (rateLimitService.checkTurnRate(callerParticipant.getId())) {
            throw new RateLimitExceededException("Slow down");
        }
        UUID userId = com.diplom.chatservice.security.SecurityUtils.getUserIdOrNull(principal);
        if (userId != null && rateLimitService.isOverDailyBudget(userId)) {
            throw new RateLimitExceededException("Daily usage limit reached");
        }

        // Transaction 1
        Turn userTurn = turnPersistenceService.persistUserTurn(roomId, callerParticipant.getId(), request.text());

        return new SubmitTurnResponse(mapToResponse(userTurn), mapToResponse(executeAiStep(roomId)));
    }

    public SubmitTurnResponse retryTurn(UUID roomId, Object principal) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        RoomParticipant callerParticipant = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(principal, roomId, participantRepository);
        validateSubmitPreconditions(room, callerParticipant);

        List<Turn> allTurns = loadHistory(roomId);
        if (allTurns.isEmpty() || allTurns.get(allTurns.size() - 1).getRoleId() != 1) { // 1=USER
            throw new InvalidRoomStateException("No pending AI response to retry");
        }

        // Phase 4d Rate Limit Checks
        if (rateLimitService.checkTurnRate(callerParticipant.getId())) {
            throw new RateLimitExceededException("Slow down");
        }
        UUID userId = com.diplom.chatservice.security.SecurityUtils.getUserIdOrNull(principal);
        if (userId != null && rateLimitService.isOverDailyBudget(userId)) {
            throw new RateLimitExceededException("Daily usage limit reached");
        }

        // Transaction 1r
        turnPersistenceService.setAiProcessing(roomId);

        return new SubmitTurnResponse(null, mapToResponse(executeAiStep(roomId)));
    }

    /**
     * Executes the AI pipeline asynchronously or synchronously.
     * sequence: load turns+participants → assembler.assemble → llmClient.complete
     * → persist ASSISTANT turn → flip floor → set phase=A_COMPOSING.
     * On failure, it throws the exception so the caller can handle it
     * (or it rethrows LlmUnavailableException).
     */
    public Turn executeAiStep(UUID roomId) {
        Room updatedRoom = roomRepository.findById(roomId).orElseThrow();
        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        List<Turn> allTurns = loadHistory(roomId);

        // Phase 4c-2b overflow folding check
        handleOverflowFolding(updatedRoom, participants, allTurns);
        // Reload room in case running_summary or summarized_through_seq was updated
        updatedRoom = roomRepository.findById(roomId).orElseThrow();

        LlmRequest llmRequest = conversationAssembler.assemble(updatedRoom, participants, allTurns);

        LlmResponse llmResponse;
        try {
            log.info("LLM call start roomId={}", roomId);
            long llmStartMs = System.currentTimeMillis();
            llmResponse = llmClient.complete(llmRequest);
            log.info(
                    "LLM call end roomId={} latencyMs={} promptTokens={} completionTokens={}",
                    roomId,
                    System.currentTimeMillis() - llmStartMs,
                    llmResponse.promptTokens(),
                    llmResponse.completionTokens()
            );
        } catch (LlmUnavailableException e) {
            turnPersistenceService.handleAiFailure(roomId);
            throw e;
        }

        // Phase 4d Token Accounting
        try {
            rateLimitService.addDailyTokens(updatedRoom.getCurrentFloorParticipantId(), llmResponse.promptTokens() + llmResponse.completionTokens());
        } catch (Exception e) {
            log.warn("Failed to account tokens for user {} in room {}", updatedRoom.getCurrentFloorParticipantId(), roomId, e);
        }

        // Transaction 2s
        return turnPersistenceService.persistAssistantTurn(roomId, llmResponse.content(), llmResponse.promptTokens(), llmResponse.completionTokens());
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

    private void handleOverflowFolding(Room room, List<RoomParticipant> participants, List<Turn> allTurns) {
        if (room.getTypeId() != 1) { // Only PAIRED rooms
            return;
        }

        int currentSummarizedThrough = room.getSummarizedThroughSeq() != null ? room.getSummarizedThroughSeq() : 0;
        int maxSeq = allTurns.isEmpty() ? 0 : allTurns.get(allTurns.size() - 1).getSeq();

        int recentVerbatimTurns = llmProperties.context().recentTurnsVerbatim();
        int candidateThroughSeq = maxSeq - recentVerbatimTurns;

        if (candidateThroughSeq <= currentSummarizedThrough) {
            return; // Not enough foldable turns beyond what's already summarized and the required verbatim tail
        }

        // Estimate tokens
        // For simplicity, we just estimate the sum of all turns in the verbatim tail + existing summary
        // This relies on the ConversationAssembler's identical char/4 heuristic.
        int verbatimTokens = 0;
        for (Turn t : allTurns) {
            if (t.getSeq() > currentSummarizedThrough && t.getRoleId() != 3) {
                verbatimTokens += estimateTokens(t.getContent());
            }
        }
        
        int summaryTokens = room.getRunningSummary() != null ? estimateTokens(room.getRunningSummary()) : 0;
        // system+context is roughly constant, we can add a flat 500 tokens buffer for system prompts and context block
        int totalEstimate = verbatimTokens + summaryTokens + 500;
        
        int inputBudget = llmProperties.promptTokenBudget() - llmProperties.maxOutputTokens();

        if (totalEstimate > inputBudget) {
            log.info("Room {} exceeded input budget (est: {}, budget: {}), triggering fold up to seq {}", 
                    room.getId(), totalEstimate, inputBudget, candidateThroughSeq);
            summarizationService.foldTurnsIntoSummary(room.getId(), candidateThroughSeq);
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }
}
