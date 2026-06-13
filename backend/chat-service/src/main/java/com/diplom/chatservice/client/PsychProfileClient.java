package com.diplom.chatservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Internal service-authenticated client for fetching a user's psych_profile.about
 * from user-service's /internal/v1/users/{userId}/psych-profile endpoint (Phase 4c-pre).
 *
 * Uses X-Internal-Api-Key header (shared secret) — NOT a JWT.
 * GDPR Art. 9: NEVER logs the about/psych_profile content.
 */
@Component
@Slf4j
public class PsychProfileClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;
    private final int aboutMaxChars;

    public PsychProfileClient(
            RestTemplateBuilder builder,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal.api-key}") String internalApiKey,
            @Value("${chat.llm.context.about-max-chars:600}") int aboutMaxChars
    ) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
        this.aboutMaxChars = aboutMaxChars;
    }

    /**
     * Fetch the "about" string from a user's psych_profile.
     * Returns null on any failure (graceful degradation per §11.5).
     *
     * @param userId the user whose psych_profile to fetch
     * @return the "about" text (truncated to about-max-chars) or null
     */
    public String getAbout(UUID userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/internal/v1/users/" + userId + "/psych-profile",
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            log.debug("PsychProfile fetch userId={} httpStatus={}", userId, response.getStatusCode().value());

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.debug("PsychProfile fetch userId={} returned non-200 or empty body", userId);
                return null;
            }

            Map<?, ?> body = response.getBody();
            java.util.function.Function<Object, String> getTrimmed = (obj) -> {
                if (obj == null) return null;
                String s = obj.toString().trim();
                return s.isBlank() ? null : s;
            };

            String aboutSelf = getTrimmed.apply(body.get("about_self"));
            if (aboutSelf == null) {
                aboutSelf = getTrimmed.apply(body.get("about"));
            }
            String reason = getTrimmed.apply(body.get("reason"));
            String goals = getTrimmed.apply(body.get("goals"));
            String priorExp = getTrimmed.apply(body.get("prior_experience"));

            String priorLabel = null;
            if ("none".equals(priorExp)) priorLabel = "нет";
            else if ("some".equals(priorExp)) priorLabel = "немного";
            else if ("extensive".equals(priorExp)) priorLabel = "большой";

            java.util.List<String> parts = new java.util.ArrayList<>();
            if (aboutSelf != null) parts.add("О себе: " + aboutSelf + ".");
            if (reason != null) parts.add("Что привело: " + reason + ".");
            if (goals != null) parts.add("Хочет поработать над: " + goals + ".");
            if (priorLabel != null) parts.add("Опыт терапии: " + priorLabel + ".");

            if (parts.isEmpty()) {
                return null;
            }

            String about = String.join(" ", parts);

            // Truncate to the configured sub-budget
            if (about.length() > aboutMaxChars) {
                about = about.substring(0, aboutMaxChars);
            }

            return about;
        } catch (Exception e) {
            // Graceful: any failure → return null (no "about" line)
            log.warn("PsychProfile fetch failed userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
