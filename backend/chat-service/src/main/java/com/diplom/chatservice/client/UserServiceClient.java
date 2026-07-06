package com.diplom.chatservice.client;

import com.diplom.chatservice.dto.UserBatchResponse;
import com.diplom.chatservice.exception.UserServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
 * Synchronous HTTP client for user-service profile lookups.
 * Mirrors content-service's UserServiceClient pattern but accepts the JWT
 * as an explicit parameter (needed for WS snapshot where there is no
 * servlet request context to extract the token from).
 */
@Component
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UserServiceClient(
        RestTemplateBuilder builder,
        @Value("${user-service.base-url}") String baseUrl
    ) {
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(3))
            .build();
        this.baseUrl = baseUrl;
    }

    /**
     * Batch-fetch user profiles from user-service.
     *
     * @param ids the user IDs to look up
     * @param jwt the raw JWT token (without "Bearer " prefix) to forward
     * @return list of profiles returned by user-service; missing users are absent
     */
    public List<UserBatchResponse> batchGetProfiles(Collection<UUID> ids, String jwt) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);

        Map<String, Object> body = Map.of("ids", new ArrayList<>(ids));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<UserBatchResponse[]> response = restTemplate.exchange(
                baseUrl + "/api/v1/users/batch",
                HttpMethod.POST,
                request,
                UserBatchResponse[].class
            );
            UserBatchResponse[] arr = response.getBody();
            return arr == null ? List.of() : Arrays.asList(arr);
        } catch (HttpClientErrorException ex) {
            // 4xx — auth/contract error (e.g. 401 invalid JWT, 400 bad request)
            log.error("Auth/contract error calling user-service for ids={}: {} {}",
                ids, ex.getStatusCode(), ex.getMessage());
            throw new UserServiceUnavailableException(
                "User-service returned client error: " + ex.getStatusCode());
        } catch (RestClientException ex) {
            // Connection refused, timeout, 5xx, etc.
            log.error("Connection/timeout error fetching user profiles from user-service for ids={}",
                ids, ex);
            throw new UserServiceUnavailableException(
                "User-service is currently unavailable");
        }
    }
}

