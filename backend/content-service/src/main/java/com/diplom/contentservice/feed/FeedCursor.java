package com.diplom.contentservice.feed;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public record FeedCursor(Long sortValue, OffsetDateTime publishedAt, UUID id) {

    public static String encode(Long sortValue, OffsetDateTime publishedAt, UUID id) {
        String svPart = sortValue == null ? "" : sortValue.toString();
        String raw = svPart + "::" + publishedAt.toString() + "::" + id.toString();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static FeedCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor),
                                    StandardCharsets.UTF_8);
            String[] parts = raw.split("::");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid cursor structure");
            }
            Long sv = parts[0].isEmpty() ? null : Long.parseLong(parts[0]);
            OffsetDateTime pa = OffsetDateTime.parse(parts[1]);
            UUID id = UUID.fromString(parts[2]);
            return new FeedCursor(sv, pa, id);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }
}
