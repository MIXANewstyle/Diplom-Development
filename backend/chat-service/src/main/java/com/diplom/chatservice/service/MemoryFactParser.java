package com.diplom.chatservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses the fact-extraction model output. One operation per line:
 * <pre>
 * + PERSON | Лера — партнёрша, отношения около двух лет
 * ~ 3 | уточнённая формулировка факта №3
 * - 5
 * </pre>
 * Unknown lines are ignored; malformed operations are dropped, never guessed.
 */
public final class MemoryFactParser {

    public static final int CAT_PERSON = 1;
    public static final int CAT_THEME = 2;
    public static final int CAT_GOAL = 3;
    public static final int CAT_TRIGGER = 4;
    public static final int CAT_VALUE = 5;
    public static final int CAT_OTHER = 6;

    public static final int MAX_CONTENT_CHARS = 300;

    public enum Kind { ADD, UPDATE, DELETE }

    public record Op(Kind kind, Integer categoryId, Integer ref, String content) {}

    private MemoryFactParser() {}

    public static List<Op> parse(String text) {
        List<Op> ops = new ArrayList<>();
        if (text == null) return ops;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            char op = line.charAt(0);
            String rest = line.substring(1).strip();
            switch (op) {
                case '+' -> {
                    int bar = rest.indexOf('|');
                    if (bar < 0) continue;
                    Integer cat = category(rest.substring(0, bar).strip());
                    String content = clean(rest.substring(bar + 1));
                    if (cat == null || content.isEmpty()) continue;
                    ops.add(new Op(Kind.ADD, cat, null, content));
                }
                case '~' -> {
                    int bar = rest.indexOf('|');
                    if (bar < 0) continue;
                    Integer ref = ref(rest.substring(0, bar));
                    String content = clean(rest.substring(bar + 1));
                    if (ref == null || content.isEmpty()) continue;
                    ops.add(new Op(Kind.UPDATE, null, ref, content));
                }
                case '-', '−', '–' -> {
                    Integer ref = ref(rest);
                    if (ref == null) continue;
                    ops.add(new Op(Kind.DELETE, null, ref, null));
                }
                default -> { /* commentary or heading — ignore */ }
            }
        }
        return ops;
    }

    public static String categoryName(int id) {
        return switch (id) {
            case CAT_PERSON -> "PERSON";
            case CAT_THEME -> "THEME";
            case CAT_GOAL -> "GOAL";
            case CAT_TRIGGER -> "TRIGGER";
            case CAT_VALUE -> "VALUE";
            default -> "OTHER";
        };
    }

    public static String categoryLabelRu(int id) {
        return switch (id) {
            case CAT_PERSON -> "Люди";
            case CAT_THEME -> "Повторяющиеся темы";
            case CAT_GOAL -> "Цели";
            case CAT_TRIGGER -> "Триггеры";
            case CAT_VALUE -> "Ценности";
            default -> "Другое";
        };
    }

    static Integer category(String s) {
        String u = s.strip().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "PERSON", "ЧЕЛОВЕК", "ЛЮДИ" -> CAT_PERSON;
            case "THEME", "ТЕМА", "ТЕМЫ" -> CAT_THEME;
            case "GOAL", "ЦЕЛЬ", "ЦЕЛИ" -> CAT_GOAL;
            case "TRIGGER", "ТРИГГЕР", "ТРИГГЕРЫ" -> CAT_TRIGGER;
            case "VALUE", "ЦЕННОСТЬ", "ЦЕННОСТИ" -> CAT_VALUE;
            case "OTHER", "ДРУГОЕ" -> CAT_OTHER;
            default -> null;
        };
    }

    private static Integer ref(String s) {
        String digits = s.strip().replace("#", "").replace("№", "").strip();
        if (digits.isEmpty()) return null;
        try {
            int n = Integer.parseInt(digits);
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String s) {
        String c = s.strip().replaceAll("\\s+", " ");
        if (c.length() > MAX_CONTENT_CHARS) {
            c = c.substring(0, MAX_CONTENT_CHARS - 1) + "…";
        }
        return c;
    }
}
