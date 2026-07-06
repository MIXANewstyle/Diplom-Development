package com.diplom.chatservice.client;

import com.diplom.chatservice.dto.UserBatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal service-authenticated client for batch user profile lookups
 * via user-service's POST /internal/v1/users/batch endpoint.
 *
 * Uses X-Internal-Api-Key header (shared secret) — NOT a JWT.
 * Mirrors the PsychProfileClient auth pattern.
 *
 * Used for paths where no user JWT is available (invite landing, WS snapshot).
 */
@Component
@Slf4j
public class InternalUserBatchClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public InternalUserBatchClient(
            RestTemplateBuilder builder,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal.api-key}") String internalApiKey
    ) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    /**
     * Batch-fetch user profiles from user-service via internal API-key auth.
     * Returns null on any failure (graceful degradation).
     *
     * @param ids the user IDs to look up (max 100)
     * @return list of profiles, or null on failure
     */
    public List<UserBatchResponse> batchGetProfiles(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Api-Key", internalApiKey);

            Map<String, Object> body = Map.of("ids", new ArrayList<>(ids));
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<UserBatchResponse[]> response = restTemplate.exchange(
                    baseUrl + "/internal/v1/users/batch",
                    HttpMethod.POST,
                    request,
                    UserBatchResponse[].class
            );

            UserBatchResponse[] arr = response.getBody();
            return arr == null ? List.of() : Arrays.asList(arr);
        } catch (RestClientException ex) {
            log.warn("Internal batch profile fetch failed for {} id(s): {}",
                    ids.size(), ex.getMessage());
            return null;
        }
    }
}
