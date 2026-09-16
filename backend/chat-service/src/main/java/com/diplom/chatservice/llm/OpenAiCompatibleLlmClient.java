package com.diplom.chatservice.llm;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.exception.LlmUnavailableException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic OpenAI-compatible chat/completions client (OpenRouter, Gemini OpenAI-compat, OpenAI, ...).
 *
 * <p>Provider-independent {@link LlmRequest} cache boundaries are translated into
 * {@code cache_control: {type: "ephemeral"}} content parts when {@code chat.llm.openrouter.cache-control}
 * is enabled (OpenRouter passes them through to Anthropic models; other models ignore them).
 * With {@code chat.llm.openrouter.usage-accounting}, the request asks for {@code usage.cost} and the
 * cached-token breakdown, which are surfaced on {@link LlmResponse}.
 */
@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final AtomicLong REQUEST_SEQ = new AtomicLong();
    private static final Path PAYLOAD_LOG_FILE = Path.of("llm-payload.log");
    private static final Object PAYLOAD_LOG_LOCK = new Object();
    private static final Map<String, String> EPHEMERAL = Map.of("type", "ephemeral");

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private String baseUrl;
    private final String model;
    private final String apiKey;
    private final int maxRetries;
    private final boolean logPayload;
    private final boolean usageAccounting;
    private final boolean cacheControl;
    private final String appTitle;

    public OpenAiCompatibleLlmClient(
            RestTemplateBuilder restTemplateBuilder,
            ChatLlmProperties properties,
            MeterRegistry meterRegistry) {

        this.baseUrl = properties.baseUrl();
        this.model = properties.model();
        this.apiKey = properties.apiKey();
        this.maxRetries = properties.maxRetries();
        this.logPayload = properties.logPayload();
        this.meterRegistry = meterRegistry;
        ChatLlmProperties.OpenRouterProps or = properties.openrouter();
        this.usageAccounting = or != null && or.usageAccounting();
        this.cacheControl = or != null && or.cacheControl();
        this.appTitle = or != null ? or.appTitle() : null;

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .build();

        if (this.logPayload) {
            log.warn("chat.llm.log-payload is ENABLED — full conversation content is being written to logs and {} (UTF-8). NEVER enable in production.",
                    PAYLOAD_LOG_FILE.toAbsolutePath());
        }
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.baseUrl = this.baseUrl != null && this.baseUrl.endsWith("/") ? this.baseUrl : this.baseUrl + "/";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String url = this.baseUrl + "chat/completions";
        String effectiveModel = request.model() != null && !request.model().isBlank() ? request.model() : this.model;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(this.apiKey);
        if (appTitle != null && !appTitle.isBlank()) {
            headers.set("X-Title", appTitle);
        }

        boolean useParts = this.cacheControl && request.hasCacheBoundaries();
        List<OpenAiMessage> openAiMessages = buildMessages(request, useParts);

        OpenAiRequest openAiRequest = new OpenAiRequest(
                effectiveModel,
                request.maxOutputTokens(),
                request.temperature(),
                openAiMessages,
                this.usageAccounting ? Map.of("include", true) : null
        );

        HttpEntity<OpenAiRequest> entity = new HttpEntity<>(openAiRequest, headers);

        int attempt = 0;
        long backoffMs = 1000;

        while (true) {
            attempt++;
            long seq = 0;
            if (this.logPayload) {
                seq = REQUEST_SEQ.incrementAndGet();
                logLlmRequestPayload(seq, attempt, url, openAiRequest);
            }
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
                        Long retryAfterSeconds = null;
                        String retryAfterHeader = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
                        if (retryAfterHeader != null) {
                            try {
                                retryAfterSeconds = Long.parseLong(retryAfterHeader);
                            } catch (NumberFormatException ignored) {}
                        }
                        log.warn("LLM complete failed with 429 Too Many Requests: status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
                        throw new com.diplom.chatservice.exception.LlmRateLimitedException("LLM rate limit exceeded. Try again in a moment.", retryAfterSeconds);
                    } else {
                        log.error("LLM complete failed with 4xx error: status={}, model={}", e.getStatusCode().value(), effectiveModel);
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
                Integer cachedTokens = body.usage() != null && body.usage().promptTokensDetails() != null
                        ? body.usage().promptTokensDetails().cachedTokens() : null;
                BigDecimal cost = body.usage() != null ? body.usage().cost() : null;

                if (promptTokens != null) {
                    meterRegistry.counter("chat.llm.tokens.input").increment(promptTokens);
                }
                if (completionTokens != null) {
                    meterRegistry.counter("chat.llm.tokens.output").increment(completionTokens);
                }
                if (cachedTokens != null) {
                    meterRegistry.counter("chat.llm.tokens.cached").increment(cachedTokens);
                }

                log.info("LLM complete success: model={}, latencyMs={}, promptTokens={}, cachedTokens={}, completionTokens={}, cost={}, status={}",
                        effectiveModel, latency, promptTokens, cachedTokens, completionTokens, cost, response.getStatusCode().value());

                if (this.logPayload) {
                    logLlmResponse(seq, content, promptTokens, completionTokens);
                }

                return new LlmResponse(content, promptTokens, completionTokens, finishReason, cachedTokens, cost);

            } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
                // Caught above to handle retries and metrics, fall through to backoff
            } catch (LlmUnavailableException | com.diplom.chatservice.exception.LlmRateLimitedException e) {
                throw e; // rethrow empty response or rate limit
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

    /**
     * Builds the wire messages. Without cache parts the system prompt is one plain string message
     * (maximum provider compatibility). With cache parts, the system prompt and marked messages are
     * sent as content arrays with {@code cache_control} on the boundary parts.
     */
    private List<OpenAiMessage> buildMessages(LlmRequest request, boolean useParts) {
        List<OpenAiMessage> openAiMessages = new ArrayList<>();

        if (!useParts) {
            String systemText = request.systemText();
            if (!systemText.isBlank()) {
                openAiMessages.add(new OpenAiMessage("system", systemText));
            }
            if (request.messages() != null) {
                for (LlmMessage m : request.messages()) {
                    openAiMessages.add(new OpenAiMessage(m.role(), m.content()));
                }
            }
            return openAiMessages;
        }

        if (request.system() != null && !request.system().isEmpty()) {
            List<ContentPart> parts = new ArrayList<>();
            for (LlmBlock b : request.system()) {
                if (b.text() == null || b.text().isEmpty()) continue;
                parts.add(new ContentPart("text", b.text(), b.cacheBoundary() ? EPHEMERAL : null));
            }
            if (!parts.isEmpty()) {
                openAiMessages.add(new OpenAiMessage("system", parts));
            }
        }
        if (request.messages() != null) {
            for (LlmMessage m : request.messages()) {
                if (m.cacheBoundary()) {
                    openAiMessages.add(new OpenAiMessage(m.role(),
                            List.of(new ContentPart("text", m.content(), EPHEMERAL))));
                } else {
                    openAiMessages.add(new OpenAiMessage(m.role(), m.content()));
                }
            }
        }
        return openAiMessages;
    }

    private void logLlmRequestPayload(long seq, int attempt, String url, OpenAiRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LLM REQUEST #").append(seq)
                .append(" (attempt ").append(attempt).append(") → ").append(url)
                .append(" | model=").append(request.model())
                .append(" maxTokens=").append(request.maxTokens())
                .append(" temp=").append(request.temperature())
                .append(" ===\n");
        for (OpenAiMessage msg : request.messages()) {
            String content = msg.contentAsText();
            sb.append("[").append(msg.role()).append("] (").append(content.length()).append(" chars)\n");
            sb.append(content).append('\n');
        }
        sb.append("=== END LLM REQUEST #").append(seq).append(" ===");
        appendPayloadLog(sb.toString());
    }

    private void logLlmResponse(long seq, String content, Integer promptTokens, Integer completionTokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LLM RESPONSE #").append(seq).append(" ===\n");
        sb.append(content != null ? content : "").append('\n');
        sb.append("usage: promptTokens=").append(promptTokens)
                .append(" completionTokens=").append(completionTokens);
        appendPayloadLog(sb.toString());
    }

    private void appendPayloadLog(String text) {
        log.info(text);
        synchronized (PAYLOAD_LOG_LOCK) {
            try {
                Files.writeString(
                        PAYLOAD_LOG_FILE,
                        text + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warn("Failed to append LLM payload log file {}: {}", PAYLOAD_LOG_FILE.toAbsolutePath(), e.getMessage());
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ContentPart(
            String type,
            String text,
            @JsonProperty("cache_control") Map<String, String> cacheControl
    ) {}

    /** Request-side message: {@code content} is either a String or a List of {@link ContentPart}. */
    private record OpenAiMessage(String role, Object content) {
        @SuppressWarnings("unchecked")
        String contentAsText() {
            if (content == null) return "";
            if (content instanceof String s) return s;
            if (content instanceof List<?> parts) {
                StringBuilder sb = new StringBuilder();
                for (Object p : parts) {
                    if (p instanceof ContentPart cp && cp.text() != null) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(cp.text());
                        if (cp.cacheControl() != null) sb.append("\n[cache_control: ephemeral]");
                    }
                }
                return sb.toString();
            }
            return String.valueOf(content);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record OpenAiRequest(
            String model,
            @JsonProperty("max_tokens") Integer maxTokens,
            Double temperature,
            List<OpenAiMessage> messages,
            Map<String, Boolean> usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(
            List<Choice> choices,
            Usage usage
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Choice(
                ResponseMessage message,
                @JsonProperty("finish_reason") String finishReason
        ) {}
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ResponseMessage(String role, String content) {}
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Usage(
                @JsonProperty("prompt_tokens") Integer promptTokens,
                @JsonProperty("completion_tokens") Integer completionTokens,
                @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails,
                BigDecimal cost
        ) {}
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PromptTokensDetails(
                @JsonProperty("cached_tokens") Integer cachedTokens
        ) {}
    }
}
