package com.diplom.contentservice.feed;

public enum SortMode {
    NEWEST,
    MOST_LIKED,
    MOST_COMMENTED;
    // FOLLOWING — Phase 6.2

    public static SortMode fromQueryParam(String s) {
        if (s == null || s.isBlank()) return NEWEST;
        try {
            return SortMode.valueOf(s.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown sort mode: " + s);
        }
    }
}
