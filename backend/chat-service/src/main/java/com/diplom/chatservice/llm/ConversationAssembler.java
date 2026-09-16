package com.diplom.chatservice.llm;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.service.DiaryContextService;
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

    private static final int SOLO_MODE_DIARY = 2;

    private final ChatLlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final RoomRepository roomRepository;
    private final DiaryContextService diaryContextService;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public LlmRequest assemble(Room room, List<RoomParticipant> participants, List<Turn> allTurns) {
        return assemble(room, participants, allTurns, null);
    }

    /**
     * @param ragBlock diary only: the volatile "[Память дневника]" block retrieved for the latest
     *                 user message; prepended to that message so the cached prefix stays intact
     */
    public LlmRequest assemble(Room room, List<RoomParticipant> participants, List<Turn> allTurns, String ragBlock) {
        boolean isPaired = room.getTypeId() == 1; // 1=PAIRED
        boolean isDiary = isDiary(room);

        String systemPromptBase;
        String contextBlock;
        int promptTokenBudget;
        int maxOutputTokens;
        String model = null;
        if (isDiary) {
            systemPromptBase = llmProperties.prompts().diarySystem();
            contextBlock = buildDiaryContextBlock(room, participants);
            promptTokenBudget = llmProperties.diary().promptTokenBudget();
            maxOutputTokens = llmProperties.diary().maxOutputTokens();
            model = llmProperties.models() != null ? llmProperties.models().diaryOrNull() : null;
        } else {
            systemPromptBase = isPaired ? llmProperties.prompts().pairedSystem() : llmProperties.prompts().soloSystem();
            contextBlock = buildContextBlock(room, participants, isPaired);
            promptTokenBudget = llmProperties.promptTokenBudget();
            maxOutputTokens = llmProperties.maxOutputTokens();
        }
        String finalSystemPrompt = systemPromptBase.replace("{context_block}", contextBlock);

        List<LlmMessage> messages = new ArrayList<>();

        // Phase 4c-2b: Prepend THIS room's rolling summary if present
        if (room.getRunningSummary() != null && !room.getRunningSummary().isBlank()) {
            messages.add(new LlmMessage("assistant", "Ранее в этом диалоге: " + room.getRunningSummary()));
        }

        int systemTokens = estimateTokens(finalSystemPrompt);
        // Estimate the rolling summary tokens
        int summaryTokens = room.getRunningSummary() != null ? estimateTokens(room.getRunningSummary()) : 0;
        int ragTokens = ragBlock != null ? estimateTokens(ragBlock) : 0;

        int availableTokens = promptTokenBudget - maxOutputTokens - systemTokens - summaryTokens - ragTokens;

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

        if (isDiary) {
            attachRagToLastUserMessage(messages, ragBlock);
            markCacheBoundaryBeforeLastUser(messages);
            return new LlmRequest(
                    List.of(LlmBlock.cached(finalSystemPrompt)),
                    messages,
                    maxOutputTokens,
                    llmProperties.temperature(),
                    model
            );
        }

        return new LlmRequest(
                finalSystemPrompt,
                messages,
                maxOutputTokens,
                llmProperties.temperature()
        );
    }

    static boolean isDiary(Room room) {
        return room.getSoloModeId() != null && room.getSoloModeId() == SOLO_MODE_DIARY;
    }

    /** The retrieved memory is volatile: it goes into the latest user message, after every cache boundary. */
    static void attachRagToLastUserMessage(List<LlmMessage> messages, String ragBlock) {
        if (ragBlock == null || ragBlock.isBlank()) return;
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage m = messages.get(i);
            if ("user".equals(m.role())) {
                messages.set(i, new LlmMessage("user", ragBlock + "\n\nЗапись:\n" + m.content(), m.cacheBoundary()));
                return;
            }
        }
    }

    /** Second breakpoint: the last assistant message before the current user turn (stable history). */
    static void markCacheBoundaryBeforeLastUser(List<LlmMessage> messages) {
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) { lastUser = i; break; }
        }
        for (int i = lastUser - 1; i >= 0; i--) {
            LlmMessage m = messages.get(i);
            if ("assistant".equals(m.role())) {
                messages.set(i, new LlmMessage(m.role(), m.content(), true));
                return;
            }
        }
    }

    private String buildDiaryContextBlock(Room room, List<RoomParticipant> participants) {
        RoomParticipant author = participants.stream()
                .filter(p -> p.getRoleId() == 3 || p.getRoleId() == 1)
                .findFirst()
                .orElse(null);
        Map<String, Object> snapshot = parseSnapshot(author);
        return diaryContextService.buildContextBlock(room, snapshotDisplayName(snapshot), snapshotAbout(snapshot));
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
