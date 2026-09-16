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
import com.diplom.chatservice.llm.EmbeddingClient;
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
    private final EmbeddingClient embeddingClient;
    private final DiaryRetrievalService diaryRetrievalService;
    private final DiaryMemoryIndexer diaryMemoryIndexer;

    private static final int RAG_BLOCK_TOKENS = 2000;

    public SubmitTurnResponse submitTurn(UUID roomId, Object principal, SubmitTurnRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        RoomParticipant callerParticipant = com.diplom.chatservice.security.SecurityUtils.getParticipantOrThrow(principal, roomId, participantRepository);

        validateSubmitPreconditions(room, callerParticipant);
        
        // Phase 4d Rate Limit Checks
        if (rateLimitService.checkTurnRate(callerParticipant.getId())) {
            throw new RateLimitExceededException("Slow down");
        }
        checkDailyBudget(room, principal);

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
        checkDailyBudget(room, principal);

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

        // Diary: embed the latest user message once — used for retrieval now and for indexing below.
        boolean isDiary = isDiary(updatedRoom);
        Turn lastUserTurn = null;
        float[] queryVector = null;
        String ragBlock = null;
        if (isDiary) {
            lastUserTurn = findLastUserTurn(allTurns);
            if (lastUserTurn != null) {
                try {
                    queryVector = embeddingClient.embedOne(lastUserTurn.getContent());
                    ragBlock = diaryRetrievalService.buildRagBlock(
                            updatedRoom.getOwnerUserId(), updatedRoom.getDiaryDate(), queryVector, RAG_BLOCK_TOKENS * 4);
                } catch (Exception e) {
                    log.warn("Diary retrieval unavailable for room {} — continuing without memory block: {}", roomId, e.getMessage());
                }
            }
        }

        LlmRequest llmRequest = conversationAssembler.assemble(updatedRoom, participants, allTurns, ragBlock);

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

        // Phase 4d Token Accounting — by the owner's user id (the same key isOverDailyBudget reads)
        try {
            if (isDiary) {
                rateLimitService.addDiaryDailyTokens(updatedRoom.getOwnerUserId(), llmResponse.totalTokens());
            } else {
                rateLimitService.addDailyTokens(updatedRoom.getOwnerUserId(), llmResponse.totalTokens());
            }
        } catch (Exception e) {
            log.warn("Failed to account tokens for user {} in room {}", updatedRoom.getOwnerUserId(), roomId, e);
        }

        // Transaction 2s
        Turn assistantTurn = turnPersistenceService.persistAssistantTurn(roomId, llmResponse.content(),
                llmResponse.promptTokens(), llmResponse.completionTokens(), llmResponse.costUsd());

        // Diary: the author's message joins long-term memory (best-effort; catch-up sweep covers failures)
        if (isDiary && lastUserTurn != null) {
            diaryMemoryIndexer.indexUserTurn(updatedRoom, lastUserTurn, queryVector);
        }
        return assistantTurn;
    }

    private static boolean isDiary(Room room) {
        return room.getSoloModeId() != null && room.getSoloModeId() == 2; // 2=DIARY
    }

    private static Turn findLastUserTurn(List<Turn> allTurns) {
        for (int i = allTurns.size() - 1; i >= 0; i--) {
            if (allTurns.get(i).getRoleId() == 1) return allTurns.get(i);
        }
        return null;
    }

    private void checkDailyBudget(Room room, Object principal) {
        UUID userId = com.diplom.chatservice.security.SecurityUtils.getUserIdOrNull(principal);
        if (userId == null) return;
        boolean over = isDiary(room)
                ? rateLimitService.isOverDiaryDailyBudget(userId, llmProperties.diary().dailyTokenBudget())
                : rateLimitService.isOverDailyBudget(userId);
        if (over) {
            throw new RateLimitExceededException("Daily usage limit reached");
        }
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
        int cap = isDiary(room) ? llmProperties.diary().hardTurnCap() : llmProperties.hardTurnCap();
        if (turnCount >= cap) {
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
        boolean diary = isDiary(room);
        if (room.getTypeId() != 1 && !diary) { // PAIRED rooms and DIARY days
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
        
        int inputBudget = diary
                ? llmProperties.diary().promptTokenBudget() - llmProperties.diary().maxOutputTokens()
                : llmProperties.promptTokenBudget() - llmProperties.maxOutputTokens();

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
