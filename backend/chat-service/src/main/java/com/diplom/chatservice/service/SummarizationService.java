package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.event.RoomArchivedInternalEvent;
import com.diplom.chatservice.exception.LlmUnavailableException;
import com.diplom.chatservice.llm.LlmClient;
import com.diplom.chatservice.llm.LlmMessage;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.llm.LlmResponse;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummarizationService {

    private static final int ROOM_TYPE_PAIRED = 1;

    private final RoomRepository roomRepository;
    private final TurnRepository turnRepository;
    private final RoomParticipantRepository participantRepository;
    private final LlmClient llmClient;
    private final ChatLlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * Listens for the archive event AFTER the transaction commits.
     * Submits the work to the dedicated "summaryExecutor".
     */
    @Async("summaryExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRoomArchived(RoomArchivedInternalEvent event) {
        if (event.typeId() != ROOM_TYPE_PAIRED) {
            return; // Only summarize PAIRED rooms
        }
        // Archival summary covers the whole transcript
        foldTurnsIntoSummary(event.roomId(), Integer.MAX_VALUE);
    }

    /**
     * Idempotent summarization logic for folding turns up to throughSeq into the running_summary.
     */
    public void foldTurnsIntoSummary(UUID roomId, int throughSeq) {
        Room room = roomRepository.findById(roomId).orElse(null);
        
        if (room == null || room.getTypeId() != ROOM_TYPE_PAIRED) {
            log.debug("Skipping fold for room {} (not paired or missing)", roomId);
            return;
        }

        int currentThroughSeq = room.getSummarizedThroughSeq() != null ? room.getSummarizedThroughSeq() : 0;
        if (currentThroughSeq >= throughSeq) {
            log.debug("Skipping fold for room {} (already summarized through seq {})", roomId, currentThroughSeq);
            return;
        }

        List<Turn> turns = turnRepository.findByRoomIdOrderBySeqAsc(roomId, Pageable.unpaged()).getContent();
        
        // Filter turns to fold: seq > currentThroughSeq AND seq <= throughSeq
        List<Turn> turnsToFold = turns.stream()
                .filter(t -> t.getSeq() > currentThroughSeq && t.getSeq() <= throughSeq)
                .collect(Collectors.toList());

        if (turnsToFold.isEmpty()) {
            log.debug("Skipping fold for room {} (no turns to fold)", roomId);
            return;
        }

        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        String transcript = buildTranscript(turnsToFold, participants);
        
        String userMessageContent;
        if (room.getRunningSummary() != null && !room.getRunningSummary().isBlank()) {
            userMessageContent = "[Краткое содержание ранее: " + room.getRunningSummary() + "]\n\n" + transcript;
        } else {
            userMessageContent = transcript;
        }

        LlmRequest request = new LlmRequest(
                llmProperties.prompts().summarization(),
                List.of(new LlmMessage("user", userMessageContent)),
                llmProperties.maxOutputTokens(),
                0.3 // slightly lower temperature for summarization
        );

        LlmResponse response = executeWithRetry(request, roomId);
        if (response != null) {
            // Find the actual max seq among the turns we folded
            int actualMaxSeq = turnsToFold.stream().mapToInt(Turn::getSeq).max().orElse(throughSeq);
            // If the requested throughSeq is greater than actual max (e.g. MAX_VALUE on archive), use actual max
            int newSummarizedThroughSeq = Math.min(actualMaxSeq, throughSeq);
            
            saveSummary(roomId, response.content(), newSummarizedThroughSeq);
            log.info("Fold successful for room {}. folded through seq {}, PromptTokens={} CompletionTokens={}", 
                    roomId, newSummarizedThroughSeq, response.promptTokens(), response.completionTokens());
        } else {
            log.warn("Fold failed permanently for room {}", roomId);
        }
    }

    private LlmResponse executeWithRetry(LlmRequest request, UUID roomId) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                long startMs = System.currentTimeMillis();
                LlmResponse response = llmClient.complete(request);
                log.info("Fold LLM call took {} ms for room {}", (System.currentTimeMillis() - startMs), roomId);
                return response;
            } catch (LlmUnavailableException e) {
                if (attempt == maxRetries) {
                    log.warn("Fold LLM call failed on attempt {} for room {}", attempt, roomId);
                    return null;
                }
                try {
                    Thread.sleep(1000L * (1 << attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (Exception e) {
                log.warn("Fold LLM call failed with unexpected error for room {}", roomId, e);
                return null;
            }
        }
        return null;
    }

    @Transactional
    protected void saveSummary(UUID roomId, String summary, int throughSeq) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room != null) {
            room.setRunningSummary(summary);
            room.setSummarizedThroughSeq(throughSeq);
            roomRepository.save(room);
        }
    }

    private String buildTranscript(List<Turn> turns, List<RoomParticipant> participants) {
        StringBuilder sb = new StringBuilder();
        for (Turn t : turns) {
            if (t.getRoleId() == 3) continue; // skip SYSTEM

            if (t.getRoleId() == 2) { // ASSISTANT
                sb.append("[Медиатор]: ").append(t.getContent()).append("\n\n");
            } else if (t.getRoleId() == 1) { // USER
                String prefix = getIdentityPrefix(t.getParticipantId(), participants);
                sb.append(prefix).append(t.getContent()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private String getIdentityPrefix(UUID participantId, List<RoomParticipant> participants) {
        RoomParticipant author = participants.stream()
                .filter(p -> p.getId().equals(participantId))
                .findFirst()
                .orElse(null);

        if (author == null) return "[Участник]: ";

        String roleLabel = author.getRoleId() == 1 ? "Партнёр A" : "Партнёр B";
        String name = getDisplayName(author);
        if (name != null) {
            return "[" + roleLabel + " · " + name + "]: ";
        }
        return "[" + roleLabel + "]: ";
    }

    private String getDisplayName(RoomParticipant p) {
        if (p == null || p.getContextSnapshot() == null || p.getContextSnapshot().isBlank()) {
            return p != null && p.getGuestDisplayName() != null ? p.getGuestDisplayName() : null;
        }
        try {
            Map<String, Object> snapshot = objectMapper.readValue(p.getContextSnapshot(), MAP_TYPE);
            Object val = snapshot.get("displayName");
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
