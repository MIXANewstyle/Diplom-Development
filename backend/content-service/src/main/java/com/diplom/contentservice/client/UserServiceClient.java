package com.diplom.contentservice.client;

import com.diplom.contentservice.dto.UserBatchResponse;
import com.diplom.contentservice.exception.UserServiceUnavailableException;
import com.diplom.contentservice.security.TokenExtractor;
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

@Component
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final TokenExtractor tokenExtractor;
    private final String baseUrl;

    public UserServiceClient(
        RestTemplateBuilder builder,
        TokenExtractor tokenExtractor,
        @Value("${user-service.base-url}") String baseUrl
    ) {
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(3))
            .build();
        this.tokenExtractor = tokenExtractor;
        this.baseUrl = baseUrl;
    }

    public List<UserBatchResponse> batchGetProfiles(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, tokenExtractor.currentBearerHeader());

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
        } catch (RestClientException ex) {
            log.error("Failed to fetch user profiles from user-service for ids={}",
                ids, ex);
            throw new UserServiceUnavailableException(
                "User-service is currently unavailable");
        }
    }
}
