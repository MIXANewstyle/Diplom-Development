package com.diplom.chatservice.diary.support;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.llm.LlmClient;
import com.diplom.chatservice.llm.LlmMessage;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.llm.LlmResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic stand-in for the chat model. It recognises which diary prompt it was given
 * (by the system prompt text) and answers in the format the pipeline parses, echoing parts of the
 * input so assertions can trace content from a turn into the day summary, the facts and the
 * period summaries. Every request is recorded for inspection.
 */
public final class FakeLlmClient implements LlmClient {

    public enum Kind { CHAT, DAY_SUMMARY, WEEK_SUMMARY, MONTH_SUMMARY, FACTS, OTHER }

    public record Call(Kind kind, LlmRequest request) {
        public String input() {
            return request.messages().isEmpty() ? "" : request.messages().get(request.messages().size() - 1).content();
        }
    }

    public static final String WEEK_MARK = "WEEK-AUTO-SUMMARY";
    public static final String MONTH_MARK = "MONTH-AUTO-SUMMARY";
    public static final String FACT_TEXT = "Лера — партнёрша автора, отношения около двух лет";

    private final ChatLlmProperties.Prompts prompts;
    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    public FakeLlmClient(ChatLlmProperties properties) {
        this.prompts = properties.prompts();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Kind kind = classify(request);
        calls.add(new Call(kind, request));
        String input = request.messages().isEmpty() ? "" : request.messages().get(request.messages().size() - 1).content();
        String content = switch (kind) {
            case DAY_SUMMARY -> daySummary(input);
            case WEEK_SUMMARY -> "Итог периода: " + WEEK_MARK + ". Неделя прошла в мыслях о Лере.\n"
                    + "Динамика: от тревоги к принятию.\nПовторяющиеся темы:\n- Лера\nДословно:\n"
                    + "«боюсь что она уйдёт» (10 марта)\nОткрытые нити: разговор с Лерой";
            case MONTH_SUMMARY -> "Итог периода: " + MONTH_MARK + ". Месяц про отношения.\n"
                    + "Динамика: ровно.\nПовторяющиеся темы:\n- Лера\nДословно:\n«боюсь что она уйдёт» (10 марта)\nОткрытые нити: нет";
            case FACTS -> "+ PERSON | " + FACT_TEXT;
            default -> "Понимаю. Что для тебя сейчас в этом самое важное?";
        };
        return new LlmResponse(content, 100, 40, "stop", 0, new BigDecimal("0.000100"));
    }

    private Kind classify(LlmRequest r) {
        String sys = r.systemText();
        if (Objects.equals(sys, prompts.diaryDaySummary())) return Kind.DAY_SUMMARY;
        if (Objects.equals(sys, prompts.diaryWeekSummary())) return Kind.WEEK_SUMMARY;
        if (Objects.equals(sys, prompts.diaryMonthSummary())) return Kind.MONTH_SUMMARY;
        if (Objects.equals(sys, prompts.diaryFactExtraction())) return Kind.FACTS;
        String diaryPrefix = prompts.diarySystem().substring(0, prompts.diarySystem().indexOf("{context_block}"));
        if (sys.startsWith(diaryPrefix)) return Kind.CHAT;
        return Kind.OTHER;
    }

    /** Echo the author's lines so the summary provably comes from the transcript. */
    private static String daySummary(String transcript) {
        List<String> authorLines = new ArrayList<>();
        for (String line : transcript.split("\\r?\\n")) {
            if (line.startsWith("[Автор]: ")) authorLines.add(line.substring("[Автор]: ".length()).strip());
        }
        String first = authorLines.isEmpty() ? "…" : authorLines.get(0);
        return "Итог: " + String.join(" ", authorLines) + "\n"
                + "Ключевые фразы: «" + first + "»\n"
                + "Состояние: тревожно\n"
                + "Открытые нити: разговор с Лерой";
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public Call last(Kind kind) {
        synchronized (calls) {
            for (int i = calls.size() - 1; i >= 0; i--) {
                if (calls.get(i).kind() == kind) return calls.get(i);
            }
        }
        throw new AssertionError("No LLM call of kind " + kind + " recorded; calls=" + calls.stream().map(Call::kind).toList());
    }

    public long count(Kind kind) {
        return calls.stream().filter(c -> c.kind() == kind).count();
    }

    public static String lastUserMessage(LlmRequest r) {
        for (int i = r.messages().size() - 1; i >= 0; i--) {
            LlmMessage m = r.messages().get(i);
            if ("user".equals(m.role())) return m.content();
        }
        throw new AssertionError("No user message in request");
    }
}
