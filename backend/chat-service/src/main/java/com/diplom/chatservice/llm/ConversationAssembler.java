package com.diplom.chatservice.llm;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.repository.RoomRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationAssembler {

    private final ChatLlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final RoomRepository roomRepository;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public LlmRequest assemble(Room room, List<RoomParticipant> participants, List<Turn> allTurns) {
        boolean isPaired = room.getTypeId() == 1; // 1=PAIRED
        String systemPromptBase = isPaired ? llmProperties.prompts().pairedSystem() : llmProperties.prompts().soloSystem();

        String contextBlock = buildContextBlock(room, participants, isPaired);
        String finalSystemPrompt = systemPromptBase.replace("{context_block}", contextBlock);

        List<LlmMessage> messages = new ArrayList<>();
        
        // Phase 4c-2b: Prepend THIS room's rolling summary if present
        if (room.getRunningSummary() != null && !room.getRunningSummary().isBlank()) {
            messages.add(new LlmMessage("assistant", "Ранее в этом диалоге: " + room.getRunningSummary()));
        }

        int systemTokens = estimateTokens(finalSystemPrompt);
        // Estimate the rolling summary tokens
        int summaryTokens = room.getRunningSummary() != null ? estimateTokens(room.getRunningSummary()) : 0;
        
        int availableTokens = llmProperties.promptTokenBudget() - llmProperties.maxOutputTokens() - systemTokens - summaryTokens;

        List<Turn> turnsToInclude = selectTurnsToFitBudget(allTurns, availableTokens, room.getSummarizedThroughSeq());

        for (Turn turn : turnsToInclude) {
            if (turn.getRoleId() == 3) { // 3=SYSTEM
                log.trace("Omitted SYSTEM turn from LLM context");
                continue;
            }

            if (turn.getRoleId() == 2) { // 2=ASSISTANT
                messages.add(new LlmMessage("assistant", turn.getContent()));
            } else if (turn.getRoleId() == 1) { // 1=USER
                String content = turn.getContent();
                if (isPaired) {
                    RoomParticipant author = participants.stream()
                            .filter(p -> p.getId().equals(turn.getParticipantId()))
                            .findFirst()
                            .orElse(null);

                    if (author != null) {
                        String prefix = getIdentityPrefix(author);
                        content = prefix + content;
                    }
                }
                messages.add(new LlmMessage("user", content));
            }
        }

        return new LlmRequest(
                finalSystemPrompt,
                messages,
                llmProperties.maxOutputTokens(),
                llmProperties.temperature()
        );
    }

    private String buildContextBlock(Room room, List<RoomParticipant> participants, boolean isPaired) {
        StringBuilder sb = new StringBuilder();

        String typeLabel = isPaired ? "paired" : "solo";
        String modeLabel = !isPaired && room.getSoloModeId() != null && room.getSoloModeId() == 1
                ? "PROBLEM_SOLVING" : "";

        sb.append("Тип комнаты: ").append(typeLabel).append(".");
        if (!modeLabel.isEmpty()) {
            sb.append(" Режим: ").append(modeLabel).append(".");
        }
        sb.append("\n");

        RoomParticipant participantA = null;
        RoomParticipant participantB = null;

        for (RoomParticipant p : participants) {
            if (p.getRoleId() == 1 || p.getRoleId() == 3) { // 1=INITIATOR, 3=SOLO
                participantA = p;
            } else if (p.getRoleId() == 2) { // 2=INVITEE
                participantB = p;
            }
        }

        if (isPaired) {
            // PAIRED: Партнёр A and Партнёр B
            Map<String, Object> snapshotA = parseSnapshot(participantA);
            String nameA = snapshotDisplayName(snapshotA);
            String aboutA = snapshotAbout(snapshotA);

            sb.append("Участник A: ").append(nameA != null ? nameA : "Партнёр A");
            sb.append(". О себе: ").append(aboutA != null ? aboutA : "не указано");
            sb.append(".\n");

            if (participantB != null) {
                Map<String, Object> snapshotB = parseSnapshot(participantB);
                String nameB = snapshotDisplayName(snapshotB);
                String aboutB = snapshotAbout(snapshotB);

                sb.append("Участник B: ").append(nameB != null ? nameB : "Партнёр B");
                sb.append(". О себе: ").append(aboutB != null ? aboutB : "не указано");
                sb.append(".\n");
            }
        } else {
            // SOLO: single participant
            Map<String, Object> snapshotA = parseSnapshot(participantA);
            String nameA = snapshotDisplayName(snapshotA);
            String aboutA = snapshotAbout(snapshotA);

            sb.append("Участник: ").append(nameA != null ? nameA : "Участник");
            sb.append(". О себе: ").append(aboutA != null ? aboutA : "не указано");
            sb.append(".\n");
        }

        // Phase 4c-3: seed_summary line from seedContextRoomId
        if (room.getSeedContextRoomId() != null) {
            Room seedRoom = roomRepository.findById(room.getSeedContextRoomId()).orElse(null);
            if (seedRoom != null && seedRoom.getRunningSummary() != null) {
                sb.append("Краткое содержание предыдущего диалога: ").append(seedRoom.getRunningSummary()).append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Identity prefix for USER turns in paired rooms (§6.3).
     * "[Партнёр A · {name}]: " or "[Партнёр A]: " if name is null.
     */
    private String getIdentityPrefix(RoomParticipant p) {
        String roleLabel = p.getRoleId() == 1 ? "Партнёр A" : "Партнёр B";
        Map<String, Object> snapshot = parseSnapshot(p);
        String name = snapshotDisplayName(snapshot);

        if (name != null) {
            return "[" + roleLabel + " · " + name + "]: ";
        }
        return "[" + roleLabel + "]: ";
    }

    /**
     * Parse the context_snapshot JSONB from a participant.
     * Returns null if the snapshot is null or unparseable.
     */
    private Map<String, Object> parseSnapshot(RoomParticipant p) {
        if (p == null || p.getContextSnapshot() == null || p.getContextSnapshot().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(p.getContextSnapshot(), MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse context_snapshot for participant {}", p.getId());
            return null;
        }
    }

    private String snapshotDisplayName(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        Object val = snapshot.get("displayName");
        return val != null ? val.toString() : null;
    }

    private String snapshotAbout(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        Object val = snapshot.get("about");
        if (val == null) return null;
        String s = val.toString();
        return s.isBlank() ? null : s;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }

    private List<Turn> selectTurnsToFitBudget(List<Turn> allTurns, int availableTokens, Integer summarizedThroughSeq) {
        List<Turn> selected = new ArrayList<>();
        if (allTurns == null || allTurns.isEmpty()) {
            return selected;
        }

        int currentSummarizedThrough = summarizedThroughSeq != null ? summarizedThroughSeq : 0;
        
        // Filter out turns that are already summarized
        List<Turn> verbatimCandidates = new ArrayList<>();
        for (Turn t : allTurns) {
            if (t.getSeq() > currentSummarizedThrough) {
                verbatimCandidates.add(t);
            }
        }
        
        if (verbatimCandidates.isEmpty()) {
            return selected;
        }

        // We must always include the latest turn
        Turn lastTurn = verbatimCandidates.get(verbatimCandidates.size() - 1);
        selected.add(lastTurn);
        
        int currentTokens = estimateTokens(lastTurn.getContent());
        
        if (verbatimCandidates.size() > 1) {
            for (int i = verbatimCandidates.size() - 2; i >= 0; i--) {
                Turn turn = verbatimCandidates.get(i);
                int tokens = estimateTokens(turn.getContent());
                // include prefixes in token estimate ideally, but roughly it fits
                if (currentTokens + tokens <= availableTokens) {
                    selected.add(0, turn);
                    currentTokens += tokens;
                } else {
                    log.warn("Pathological case: even with folding, verbatim tail exceeded budget. Truncating.");
                    break;
                }
            }
        }
        return selected;
    }
}
