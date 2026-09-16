package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.DiaryMemoryFact;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.llm.LlmClient;
import com.diplom.chatservice.llm.LlmMessage;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.llm.LlmResponse;
import com.diplom.chatservice.repository.DiaryMemoryFactRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * "What the AI remembers": stable facts extracted from closed diary days and always present in the
 * context. The author sees, edits and deletes them; deletion is hard ("забыть").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryMemoryFactService {

    private final DiaryMemoryFactRepository factRepository;
    private final RoomRepository roomRepository;
    private final TurnRepository turnRepository;
    private final LlmClient llmClient;
    private final ChatLlmProperties llmProperties;
    private final RateLimitService rateLimitService;

    // ==================== read / edit ====================

    @Transactional(readOnly = true)
    public List<DiaryMemoryFact> list(UUID ownerUserId) {
        return factRepository.findByOwnerUserIdOrderByCategoryIdAscCreatedAtAsc(ownerUserId);
    }

    @Transactional
    public DiaryMemoryFact update(UUID ownerUserId, UUID factId, String content) {
        DiaryMemoryFact fact = factRepository.findByIdAndOwnerUserId(factId, ownerUserId)
                .orElseThrow(() -> new RoomNotFoundException("Fact not found: " + factId));
        fact.setContent(content.strip());
        fact.setUpdatedAt(OffsetDateTime.now());
        return factRepository.save(fact);
    }

    @Transactional
    public void delete(UUID ownerUserId, UUID factId) {
        factRepository.findByIdAndOwnerUserId(factId, ownerUserId).ifPresent(factRepository::delete);
    }

    // ==================== context ====================

    /** Facts grouped by category as a compact text block, or {@code null} when there are none. */
    @Transactional(readOnly = true)
    public String buildFactsBlock(UUID ownerUserId, int maxChars) {
        List<DiaryMemoryFact> facts = list(ownerUserId);
        if (facts.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("Что известно об авторе из прошлых записей (устойчивые факты):\n");
        int currentCat = -1;
        for (DiaryMemoryFact f : facts) {
            if (f.getCategoryId() != currentCat) {
                currentCat = f.getCategoryId();
                sb.append(MemoryFactParser.categoryLabelRu(currentCat)).append(":\n");
            }
            String line = "- " + f.getContent() + "\n";
            if (sb.length() + line.length() > maxChars) break;
            sb.append(line);
        }
        return sb.toString().strip();
    }

    // ==================== extraction ====================

    /**
     * Runs the extraction prompt over a closed day and applies the returned operations.
     * Called after the day summary is saved; failures are logged and never propagate.
     */
    public void extractFromDay(UUID roomId) {
        try {
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null || room.getDiaryDate() == null) return;
            List<Turn> turns = turnRepository.findByRoomIdOrderBySeqAsc(roomId);
            String transcript = transcript(turns);
            if (transcript.isBlank()) return;

            List<DiaryMemoryFact> existing = list(room.getOwnerUserId());
            String prompt = llmProperties.prompts().diaryFactExtraction();
            String input = "Текущие факты:\n" + numbered(existing) + "\n\nЗапись за "
                    + room.getDiaryDate() + ":\n" + transcript;

            LlmRequest request = new LlmRequest(prompt, List.of(new LlmMessage("user", input)),
                    Math.min(llmProperties.maxOutputTokens(), 1200), 0.2, llmProperties.models().factsOrNull());
            LlmResponse response = llmClient.complete(request);
            rateLimitService.addDiaryDailyTokens(room.getOwnerUserId(), response.totalTokens());

            int applied = apply(room, existing, MemoryFactParser.parse(response.content()));
            log.info("Diary facts: room {} applied {} op(s), promptTokens={} completionTokens={}",
                    roomId, applied, response.promptTokens(), response.completionTokens());
        } catch (Exception e) {
            log.warn("Diary fact extraction failed for room {}: {}", roomId, e.getMessage());
        }
    }

    int apply(Room room, List<DiaryMemoryFact> existing, List<MemoryFactParser.Op> ops) {
        int maxFacts = llmProperties.diary().maxFacts();
        long count = factRepository.countByOwnerUserId(room.getOwnerUserId());
        LocalDate date = room.getDiaryDate();
        int applied = 0;
        List<DiaryMemoryFact> added = new ArrayList<>();
        for (MemoryFactParser.Op op : ops) {
            switch (op.kind()) {
                case ADD -> {
                    if (count >= maxFacts) continue;
                    if (isDuplicate(existing, added, op.content())) continue;
                    DiaryMemoryFact f = DiaryMemoryFact.builder()
                            .ownerUserId(room.getOwnerUserId())
                            .categoryId(op.categoryId())
                            .content(op.content())
                            .firstSeenDate(date)
                            .lastConfirmedDate(date)
                            .sourceRoomId(room.getId())
                            .build();
                    added.add(factRepository.save(f));
                    count++;
                    applied++;
                }
                case UPDATE -> {
                    Optional<DiaryMemoryFact> target = byRef(existing, op.ref());
                    if (target.isEmpty()) continue;
                    DiaryMemoryFact f = target.get();
                    f.setContent(op.content());
                    f.setLastConfirmedDate(date);
                    f.setUpdatedAt(OffsetDateTime.now());
                    factRepository.save(f);
                    applied++;
                }
                case DELETE -> {
                    Optional<DiaryMemoryFact> target = byRef(existing, op.ref());
                    if (target.isEmpty()) continue;
                    factRepository.delete(target.get());
                    count--;
                    applied++;
                }
            }
        }
        return applied;
    }

    private Optional<DiaryMemoryFact> byRef(List<DiaryMemoryFact> existing, Integer ref) {
        if (ref == null || ref < 1 || ref > existing.size()) return Optional.empty();
        return Optional.of(existing.get(ref - 1));
    }

    private boolean isDuplicate(List<DiaryMemoryFact> existing, List<DiaryMemoryFact> added, String content) {
        String norm = content.toLowerCase();
        for (DiaryMemoryFact f : existing) {
            if (f.getContent().equalsIgnoreCase(content) || f.getContent().toLowerCase().contains(norm)) return true;
        }
        for (DiaryMemoryFact f : added) {
            if (f.getContent().equalsIgnoreCase(content)) return true;
        }
        return false;
    }

    /** "1. PERSON | ..." — the model refers to facts by this number. */
    static String numbered(List<DiaryMemoryFact> facts) {
        if (facts.isEmpty()) return "(пока нет)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < facts.size(); i++) {
            DiaryMemoryFact f = facts.get(i);
            sb.append(i + 1).append(". ").append(MemoryFactParser.categoryName(f.getCategoryId()))
                    .append(" | ").append(f.getContent()).append('\n');
        }
        return sb.toString().strip();
    }

    private String transcript(List<Turn> turns) {
        StringBuilder sb = new StringBuilder();
        for (Turn t : turns) {
            if (t.getRoleId() == 3) continue;
            sb.append(t.getRoleId() == 2 ? "[Собеседник]: " : "[Автор]: ").append(t.getContent()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
