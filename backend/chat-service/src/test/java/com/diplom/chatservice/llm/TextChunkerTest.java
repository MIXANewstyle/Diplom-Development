package com.diplom.chatservice.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(200);

    @Test
    void shortTextIsOneChunk() {
        assertThat(chunker.chunk("  Сегодня был странный день.  ")).containsExactly("Сегодня был странный день.");
    }

    @Test
    void blankTextYieldsNothing() {
        assertThat(chunker.chunk("   \n\n  ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void longTextSplitsOnParagraphsWithOverlap() {
        String p1 = "Первый абзац про работу и усталость, довольно длинный, чтобы занимать место.";
        String p2 = "Второй абзац про разговор с Лерой, тоже достаточно длинный для проверки.";
        String p3 = "Третий абзац про планы на завтра и что я хочу сделать иначе, чем вчера.";
        List<String> chunks = chunker.chunk(p1 + "\n\n" + p2 + "\n\n" + p3);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
        // every paragraph is present in at least one chunk
        assertThat(String.join("\n", chunks)).contains(p1).contains(p2).contains(p3);
        // overlap: p2 appears in two adjacent chunks (fits with p3 in the 200-char window)
        assertThat(chunks.stream().filter(c -> c.contains(p2)).count()).isEqualTo(2);
    }

    @Test
    void overlongParagraphIsHardSplitOnSentenceBoundary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append("Предложение номер ").append(i).append(" достаточно длинное. ");
        List<String> chunks = chunker.chunk(sb.toString());
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
        assertThat(chunks.get(0)).endsWith(".");
    }
}
