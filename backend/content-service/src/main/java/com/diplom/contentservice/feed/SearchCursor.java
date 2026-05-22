package com.diplom.contentservice.feed;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public record SearchCursor(double score, OffsetDateTime publishedAt, UUID id) {

    public static String encode(double score, OffsetDateTime publishedAt, UUID id) {
        String raw = String.format(Locale.ROOT, "%.6f", score)
            + "::" + publishedAt.toString() + "::" + id.toString();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static SearchCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor),
                                    StandardCharsets.UTF_8);
            String[] parts = raw.split("::");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid cursor structure");
            }
            return new SearchCursor(
                Double.parseDouble(parts[0]),
                OffsetDateTime.parse(parts[1]),
                UUID.fromString(parts[2])
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor);
        }
    }
}
