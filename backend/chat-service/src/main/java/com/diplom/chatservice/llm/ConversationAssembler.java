package com.diplom.chatservice.llm;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationAssembler {

    private final ChatLlmProperties llmProperties;

    public LlmRequest assemble(Room room, List<RoomParticipant> participants, List<Turn> allTurns) {
        boolean isPaired = room.getTypeId() == 1; // 1=PAIRED
        String systemPromptBase = isPaired ? llmProperties.prompts().pairedSystem() : llmProperties.prompts().soloSystem();

        String contextBlock = buildContextBlock(room, participants, isPaired);
        String finalSystemPrompt = systemPromptBase.replace("{context_block}", contextBlock);

        List<LlmMessage> messages = new ArrayList<>();
        int systemTokens = estimateTokens(finalSystemPrompt);
        int availableTokens = llmProperties.promptTokenBudget() - llmProperties.maxOutputTokens() - systemTokens;

        List<Turn> turnsToInclude = selectTurnsToFitBudget(allTurns, availableTokens);

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
                    Optional<RoomParticipant> author = participants.stream()
                            .filter(p -> p.getId().equals(turn.getParticipantId()))
                            .findFirst();

                    if (author.isPresent()) {
                        String prefix = getIdentityPrefix(author.get());
                        content = prefix + content;
                    }
                }
                messages.add(new LlmMessage("user", content));
            }
        }

        // TODO Phase 4: If room.runningSummary is not null, prepend it as a system message.

        return new LlmRequest(
                finalSystemPrompt,
                messages,
                llmProperties.maxOutputTokens(),
                llmProperties.temperature()
        );
    }

    private String buildContextBlock(Room room, List<RoomParticipant> participants, boolean isPaired) {
        String template = llmProperties.prompts().contextBlockTemplate();

        String typeLabel = isPaired ? "paired" : "solo";
        String modeLabel = !isPaired && room.getSoloModeId() != null && room.getSoloModeId() == 1 ? "PROBLEM_SOLVING" : "не указано";
        
        template = template.replace("{paired | solo}", typeLabel);
        template = template.replace("{PROBLEM_SOLVING для соло}", modeLabel);

        RoomParticipant participantA = null;
        RoomParticipant participantB = null;

        for (RoomParticipant p : participants) {
            if (p.getRoleId() == 1 || p.getRoleId() == 3) { // 1=INITIATOR, 3=SOLO
                participantA = p;
            } else if (p.getRoleId() == 2) { // 2=INVITEE
                participantB = p;
            }
        }

        template = template.replace("{displayName}{, пол: …}{, возраст: …}", getDisplayName(participantA, "Партнёр A"));
        template = template.replace("{about_A или «не указано»}", getAboutInfo(participantA));

        if (isPaired && participantB != null) {
            String bTemplate = "Участник B: " + getDisplayName(participantB, "Партнёр B") + ". О себе: " + getAboutInfo(participantB) + ".";
            template = template.replace("Участник B: {displayName}{, пол: …}{, возраст: …}. О себе: {about_B}.        // только парная", bTemplate);
        } else {
            template = template.replace("Участник B: {displayName}{, пол: …}{, возраст: …}. О себе: {about_B}.        // только парная\n", "");
        }

        if (room.getSeedContextRoomId() != null && room.getRunningSummary() != null) {
            template = template.replace("Краткое содержание предыдущего диалога: {seed_summary}.                       // если есть seedContextRoomId", "Краткое содержание предыдущего диалога: " + room.getRunningSummary());
        } else {
            template = template.replace("Краткое содержание предыдущего диалога: {seed_summary}.                       // если есть seedContextRoomId\n", "");
            template = template.replace("Краткое содержание предыдущего диалога: {seed_summary}.                       // если есть seedContextRoomId", "");
        }

        return template.trim();
    }

    private String getDisplayName(RoomParticipant p, String defaultLabel) {
        if (p == null) return defaultLabel;
        // TODO Phase 4: Identity enrichment (real names for registered users)
        return p.getGuestDisplayName() != null ? p.getGuestDisplayName() : defaultLabel;
    }

    private String getAboutInfo(RoomParticipant p) {
        if (p == null || p.getContextSnapshot() == null) return "не указано";
        // Parsing the JSONB contextSnapshot is deferred, but for Phase 2,
        // it's always null anyway, so it evaluates to "не указано".
        return "не указано";
    }

    private String getIdentityPrefix(RoomParticipant p) {
        String roleLabel = p.getRoleId() == 1 ? "Партнёр A" : "Партнёр B";
        String name = getDisplayName(p, roleLabel);
        return "[" + roleLabel + " · " + name + "]: ";
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }

    private List<Turn> selectTurnsToFitBudget(List<Turn> allTurns, int availableTokens) {
        List<Turn> selected = new ArrayList<>();
        if (allTurns == null || allTurns.isEmpty()) {
            return selected;
        }

        // We must always include the latest turn
        Turn lastTurn = allTurns.get(allTurns.size() - 1);
        selected.add(lastTurn);
        
        int currentTokens = estimateTokens(lastTurn.getContent());
        
        if (allTurns.size() > 1) {
            for (int i = allTurns.size() - 2; i >= 0; i--) {
                Turn turn = allTurns.get(i);
                int tokens = estimateTokens(turn.getContent());
                // include prefixes in token estimate ideally, but roughly it fits
                if (currentTokens + tokens <= availableTokens) {
                    selected.add(0, turn);
                    currentTokens += tokens;
                } else {
                    // TODO Phase 4: prepend running_summary instead of dropping oldest verbatim turns.
                    break;
                }
            }
        }
        return selected;
    }
}
