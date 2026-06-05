package com.diplom.chatservice.llm;

import com.diplom.chatservice.exception.LlmUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int maxRetries;

    public OpenAiCompatibleLlmClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${chat.llm.base-url}") String baseUrl,
            @Value("${chat.llm.model}") String model,
            @Value("${chat.llm.api-key}") String apiKey,
            @Value("${chat.llm.request-timeout-ms}") long timeoutMs,
            @Value("${chat.llm.max-retries}") int maxRetries) {

        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.model = model;
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String url = this.baseUrl + "chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.apiKey);

        List<OpenAiMessage> openAiMessages = new ArrayList<>();
        if (request.system() != null && !request.system().isBlank()) {
            openAiMessages.add(new OpenAiMessage("system", request.system()));
        }
        if (request.messages() != null) {
            for (LlmMessage m : request.messages()) {
                openAiMessages.add(new OpenAiMessage(m.role(), m.content()));
            }
        }

        OpenAiRequest openAiRequest = new OpenAiRequest(
                this.model,
                request.maxOutputTokens(),
                request.temperature(),
                openAiMessages
        );

        HttpEntity<OpenAiRequest> entity = new HttpEntity<>(openAiRequest, headers);

        int attempt = 0;
        long backoffMs = 1000;

        while (true) {
            attempt++;
            try {
                long startTime = System.currentTimeMillis();
                ResponseEntity<OpenAiResponse> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        OpenAiResponse.class
                );
                long latency = System.currentTimeMillis() - startTime;

                OpenAiResponse body = response.getBody();
                if (body == null || body.choices() == null || body.choices().isEmpty()) {
                    throw new LlmUnavailableException("Empty response from LLM provider");
                }

                OpenAiResponse.Choice choice = body.choices().get(0);
                String content = choice.message() != null ? choice.message().content() : null;
                String finishReason = choice.finishReason();

                Integer promptTokens = body.usage() != null ? body.usage().promptTokens() : null;
                Integer completionTokens = body.usage() != null ? body.usage().completionTokens() : null;

                log.info("LLM complete success: model={}, latencyMs={}, promptTokens={}, completionTokens={}, status={}",
                        this.model, latency, promptTokens, completionTokens, response.getStatusCode().value());

                return new LlmResponse(content, promptTokens, completionTokens, finishReason);

            } catch (HttpClientErrorException e) {
                // 4xx errors - NEVER retry, do not log body, do not leak API keys
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    if (attempt > this.maxRetries) {
                        log.error("LLM complete failed after {} attempts due to 429 Too Many Requests.", attempt);
                        throw new LlmUnavailableException("LLM provider rate limit exceeded");
                    }
                    // Retry 429
                } else {
                    log.error("LLM complete failed with 4xx error: status={}, model={}", e.getStatusCode().value(), this.model);
                    throw new LlmUnavailableException("LLM provider client error: " + e.getStatusCode().value());
                }
            } catch (HttpServerErrorException e) {
                // 5xx errors
                if (attempt > this.maxRetries) {
                    log.error("LLM complete failed after {} attempts due to 5xx error: status={}", attempt, e.getStatusCode().value());
                    throw new LlmUnavailableException("LLM provider server error: " + e.getStatusCode().value());
                }
                // Retry 5xx
            } catch (ResourceAccessException e) {
                // Timeouts and connection errors
                if (attempt > this.maxRetries) {
                    log.error("LLM complete failed after {} attempts due to network error.", attempt);
                    throw new LlmUnavailableException("LLM provider network error");
                }
                // Retry timeouts
            } catch (LlmUnavailableException e) {
                throw e; // rethrow empty response
            } catch (Exception e) {
                // Unknown exception
                log.error("LLM complete failed with unknown error."); // no stacktrace to prevent leak
                throw new LlmUnavailableException("LLM provider unknown error");
            }

            // Apply backoff before next attempt
            try {
                // exponential backoff with a bit of jitter
                long jitter = (long) (Math.random() * 500);
                Thread.sleep(backoffMs + jitter);
                backoffMs *= 2;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new LlmUnavailableException("Thread interrupted during retry backoff");
            }
        }
    }

    private record OpenAiMessage(String role, String content) {}

    private record OpenAiRequest(
            String model,
            @JsonProperty("max_tokens") Integer maxTokens,
            Double temperature,
            List<OpenAiMessage> messages
    ) {}

    private record OpenAiResponse(
            List<Choice> choices,
            Usage usage
    ) {
        public record Choice(
                OpenAiMessage message,
                @JsonProperty("finish_reason") String finishReason
        ) {}
        public record Usage(
                @JsonProperty("prompt_tokens") Integer promptTokens,
                @JsonProperty("completion_tokens") Integer completionTokens
        ) {}
    }
}
