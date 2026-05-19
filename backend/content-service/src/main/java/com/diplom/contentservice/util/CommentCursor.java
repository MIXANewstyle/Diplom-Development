package com.diplom.contentservice.util;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public record CommentCursor(OffsetDateTime createdAt, UUID id) {

    public static String encode(OffsetDateTime createdAt, UUID id) {
        String raw = createdAt.toString() + "::" + id.toString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static CommentCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("::");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid cursor format");
            return new CommentCursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }
}
