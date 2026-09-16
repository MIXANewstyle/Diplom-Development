package com.diplom.chatservice.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a diary text into embedding-sized chunks. A text at or below {@code maxChars}
 * is one chunk; longer texts are split on paragraph boundaries with one paragraph of overlap
 * so a thought that straddles a boundary is retrievable from either chunk.
 */
public final class TextChunker {

    private final int maxChars;

    public TextChunker(int maxChars) {
        if (maxChars < 200) throw new IllegalArgumentException("maxChars must be >= 200");
        this.maxChars = maxChars;
    }

    public List<String> chunk(String text) {
        if (text == null) return List.of();
        String trimmed = text.strip();
        if (trimmed.isEmpty()) return List.of();
        if (trimmed.length() <= maxChars) return List.of(trimmed);

        List<String> paragraphs = new ArrayList<>();
        for (String p : trimmed.split("\\n\\s*\\n")) {
            String s = p.strip();
            if (s.isEmpty()) continue;
            if (s.length() <= maxChars) {
                paragraphs.add(s);
            } else {
                paragraphs.addAll(splitLong(s));
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String lastParagraph = null;
        for (String p : paragraphs) {
            if (current.length() > 0 && current.length() + 2 + p.length() > maxChars) {
                chunks.add(current.toString());
                current.setLength(0);
                // overlap: start the next chunk with the previous paragraph when it fits
                if (lastParagraph != null && lastParagraph.length() + 2 + p.length() <= maxChars) {
                    current.append(lastParagraph).append("\n\n");
                }
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(p);
            lastParagraph = p;
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    /** Hard split of a single over-long paragraph on sentence/space boundaries. */
    private List<String> splitLong(String paragraph) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(paragraph.length(), start + maxChars);
            if (end < paragraph.length()) {
                int cut = lastBoundary(paragraph, start, end);
                if (cut > start) end = cut;
            }
            parts.add(paragraph.substring(start, end).strip());
            start = end;
        }
        return parts;
    }

    private int lastBoundary(String s, int from, int to) {
        for (int i = to - 1; i > from + maxChars / 2; i--) {
            char c = s.charAt(i);
            if (c == '.' || c == '!' || c == '?') return i + 1;
        }
        int space = s.lastIndexOf(' ', to - 1);
        return space > from + maxChars / 2 ? space + 1 : -1;
    }
}
