package com.diplom.chatservice.llm;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.exception.LlmUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OpenAI-compatible {@code POST {baseUrl}embeddings} client (OpenRouter, OpenAI, Gemini OpenAI-compat).
 *
 * <p>Vectors are normalized to exactly {@code dimensions} components: providers that ignore the
 * {@code dimensions} parameter and return longer MRL-style vectors are truncated + L2-normalized so
 * cosine similarity stays meaningful; shorter vectors are a hard configuration error.
 */
@Slf4j
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final int batchSize;
    private final int maxRetries;

    public OpenAiCompatibleEmbeddingClient(RestTemplateBuilder restTemplateBuilder,
                                           ChatLlmProperties properties,
                                           MeterRegistry meterRegistry) {
        ChatLlmProperties.EmbeddingsProps props = properties.embeddings();
        if (props == null) {
            throw new IllegalStateException("chat.llm.embeddings.* is not configured");
        }
        String url = props.baseUrl() != null && !props.baseUrl().isBlank() ? props.baseUrl() : properties.baseUrl();
        this.baseUrl = url.endsWith("/") ? url : url + "/";
        this.apiKey = props.apiKey() != null && !props.apiKey().isBlank() ? props.apiKey() : properties.apiKey();
        this.model = props.model();
        this.dimensions = props.dimensions();
        this.batchSize = Math.max(1, props.batchSize());
        this.maxRetries = properties.maxRetries();
        this.meterRegistry = meterRegistry;
        long timeout = props.requestTimeoutMs() > 0 ? props.requestTimeoutMs() : properties.requestTimeoutMs();
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeout))
                .setReadTimeout(Duration.ofMillis(timeout))
                .build();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) return List.of();
        List<float[]> result = new ArrayList<>(inputs.size());
        for (int from = 0; from < inputs.size(); from += batchSize) {
            List<String> batch = inputs.subList(from, Math.min(inputs.size(), from + batchSize));
            result.addAll(embedBatch(batch));
        }
        return result;
    }

    private List<float[]> embedBatch(List<String> batch) {
        String url = baseUrl + "embeddings";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(
                new EmbeddingRequest(model, batch, dimensions > 0 ? dimensions : null), headers);

        int attempt = 0;
        long backoffMs = 1000;
        while (true) {
            attempt++;
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                ResponseEntity<EmbeddingResponse> response =
                        restTemplate.exchange(url, HttpMethod.POST, entity, EmbeddingResponse.class);
                sample.stop(meterRegistry.timer("chat.embeddings.call.latency", "outcome", "success"));
                EmbeddingResponse body = response.getBody();
                if (body == null || body.data() == null || body.data().size() != batch.size()) {
                    throw new LlmUnavailableException("Embedding provider returned an unexpected number of vectors");
                }
                if (body.usage() != null && body.usage().promptTokens() != null) {
                    meterRegistry.counter("chat.embeddings.tokens.input").increment(body.usage().promptTokens());
                }
                List<EmbeddingData> sorted = new ArrayList<>(body.data());
                sorted.sort(Comparator.comparingInt(d -> d.index() != null ? d.index() : 0));
                List<float[]> vectors = new ArrayList<>(sorted.size());
                for (EmbeddingData d : sorted) {
                    vectors.add(normalize(d.embedding()));
                }
                return vectors;
            } catch (HttpClientErrorException e) {
                sample.stop(meterRegistry.timer("chat.embeddings.call.latency", "outcome", "error"));
                meterRegistry.counter("chat.embeddings.errors.total", "status", String.valueOf(e.getStatusCode().value())).increment();
                log.error("Embedding call failed with 4xx: status={}, model={}", e.getStatusCode().value(), model);
                throw new LlmUnavailableException("Embedding provider client error: " + e.getStatusCode().value());
            } catch (HttpServerErrorException | ResourceAccessException e) {
                sample.stop(meterRegistry.timer("chat.embeddings.call.latency", "outcome", "error"));
                meterRegistry.counter("chat.embeddings.errors.total", "status", "retryable").increment();
                if (attempt > maxRetries) {
                    log.error("Embedding call failed after {} attempts: {}", attempt, e.getClass().getSimpleName());
                    throw new LlmUnavailableException("Embedding provider unavailable");
                }
                try {
                    Thread.sleep(backoffMs + (long) (Math.random() * 300));
                    backoffMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmUnavailableException("Thread interrupted during embedding retry backoff");
                }
            } catch (LlmUnavailableException e) {
                throw e;
            } catch (Exception e) {
                sample.stop(meterRegistry.timer("chat.embeddings.call.latency", "outcome", "error"));
                meterRegistry.counter("chat.embeddings.errors.total", "status", "unknown").increment();
                log.error("Embedding call failed with unknown error: {}", e.getClass().getSimpleName());
                throw new LlmUnavailableException("Embedding provider unknown error");
            }
        }
    }

    private float[] normalize(List<Double> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new LlmUnavailableException("Embedding provider returned an empty vector");
        }
        if (raw.size() < dimensions) {
            throw new LlmUnavailableException("Embedding provider returned " + raw.size()
                    + " dimensions but " + dimensions + " are configured (EMBEDDINGS_DIMS)");
        }
        boolean truncated = raw.size() > dimensions;
        float[] v = new float[dimensions];
        double norm = 0;
        for (int i = 0; i < dimensions; i++) {
            v[i] = raw.get(i).floatValue();
            norm += (double) v[i] * v[i];
        }
        if (truncated) {
            // MRL-style vectors stay meaningful after truncation once re-normalized.
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dimensions; i++) v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record EmbeddingRequest(String model, List<String> input, Integer dimensions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<EmbeddingData> data, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(Integer index, List<Double> embedding) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(@com.fasterxml.jackson.annotation.JsonProperty("prompt_tokens") Integer promptTokens) {}
}
