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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private String baseUrl;
    private final String model;
    private final String apiKey;
    private final int maxRetries;

    public OpenAiCompatibleLlmClient(
            RestTemplateBuilder restTemplateBuilder,
            com.diplom.chatservice.config.ChatLlmProperties properties,
            MeterRegistry meterRegistry) {

        this.baseUrl = properties.baseUrl();
        this.model = properties.model();
        this.apiKey = properties.apiKey();
        this.maxRetries = properties.maxRetries();
        this.meterRegistry = meterRegistry;

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .build();
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.baseUrl = this.baseUrl != null && this.baseUrl.endsWith("/") ? this.baseUrl : this.baseUrl + "/";
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
                Timer.Sample sample = Timer.start(meterRegistry);
                ResponseEntity<OpenAiResponse> response;
                try {
                    response = restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            OpenAiResponse.class
                    );
                } catch (HttpClientErrorException e) {
                    sample.stop(meterRegistry.timer("chat.llm.call.latency", "outcome", "error"));
                    meterRegistry.counter("chat.llm.errors.total", "status", String.valueOf(e.getStatusCode().value())).increment();
                    // 4xx errors - NEVER retry, do not log body, do not leak API keys
                    if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        if (attempt > this.maxRetries) {
                            log.error("LLM complete failed after {} attempts due to 429 Too Many Requests.", attempt);
                            throw new LlmUnavailableException("LLM provider rate limit exceeded");
                        }
                        meterRegistry.counter("chat.llm.retries.total").increment();
                        throw e; // Caught by outer catch
                    } else {
                        log.error("LLM complete failed with 4xx error: status={}, model={}", e.getStatusCode().value(), this.model);
                        throw new LlmUnavailableException("LLM provider client error: " + e.getStatusCode().value());
                    }
                } catch (HttpServerErrorException e) {
                    sample.stop(meterRegistry.timer("chat.llm.call.latency", "outcome", "error"));
                    meterRegistry.counter("chat.llm.errors.total", "status", String.valueOf(e.getStatusCode().value())).increment();
                    // 5xx errors
                    if (attempt > this.maxRetries) {
                        log.error("LLM complete failed after {} attempts due to 5xx error: status={}", attempt, e.getStatusCode().value());
                        throw new LlmUnavailableException("LLM provider server error: " + e.getStatusCode().value());
                    }
                    meterRegistry.counter("chat.llm.retries.total").increment();
                    throw e;
                } catch (ResourceAccessException e) {
                    sample.stop(meterRegistry.timer("chat.llm.call.latency", "outcome", "timeout"));
                    meterRegistry.counter("chat.llm.errors.total", "status", "timeout").increment();
                    // Timeouts and connection errors
                    if (attempt > this.maxRetries) {
                        log.error("LLM complete failed after {} attempts due to network error.", attempt);
                        throw new LlmUnavailableException("LLM provider network error");
                    }
                    meterRegistry.counter("chat.llm.retries.total").increment();
                    throw e;
                }

                sample.stop(meterRegistry.timer("chat.llm.call.latency", "outcome", "success"));
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

                if (promptTokens != null) {
                    meterRegistry.counter("chat.llm.tokens.input").increment(promptTokens);
                }
                if (completionTokens != null) {
                    meterRegistry.counter("chat.llm.tokens.output").increment(completionTokens);
                }

                log.info("LLM complete success: model={}, latencyMs={}, promptTokens={}, completionTokens={}, status={}",
                        this.model, latency, promptTokens, completionTokens, response.getStatusCode().value());

                return new LlmResponse(content, promptTokens, completionTokens, finishReason);

            } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
                // Caught above to handle retries and metrics, fall through to backoff
            } catch (LlmUnavailableException e) {
                throw e; // rethrow empty response
            } catch (Exception e) {
                // Unknown exception
                meterRegistry.counter("chat.llm.errors.total", "status", "unknown").increment();
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
