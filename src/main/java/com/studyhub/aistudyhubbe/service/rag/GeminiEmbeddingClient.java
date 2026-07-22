package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.config.RagProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GeminiEmbeddingClient {

    private static final String RETRIEVAL_DOCUMENT = "RETRIEVAL_DOCUMENT";
    private static final String RETRIEVAL_QUERY = "RETRIEVAL_QUERY";

    private final AiProperties aiProperties;
    private final RagProperties ragProperties;

    public GeminiEmbeddingClient(AiProperties aiProperties, RagProperties ragProperties) {
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
    }

    public boolean isConfigured() {
        return ragProperties.isEmbeddingEnabled()
                && StringUtils.hasText(aiProperties.getGeminiEmbedding().getApiKey());
    }

    public List<float[]> embedDocuments(List<String> texts, String documentTitle) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        requireConfigured();

        int batchSize = Math.max(1, Math.min(ragProperties.getEmbeddingBatchSize(), 100));
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            vectors.addAll(embedBatch(texts.subList(start, end), documentTitle));
        }
        return vectors;
    }

    public float[] embedQuestion(String text) {
        if (!StringUtils.hasText(text)) {
            return new float[0];
        }
        requireConfigured();

        try {
            EmbedResponse response = restClient()
                    .post()
                    .uri("/%s:embedContent".formatted(modelPath()))
                    .header("x-goog-api-key", aiProperties.getGeminiEmbedding().getApiKey())
                    .body(buildEmbedRequest(text.trim(), RETRIEVAL_QUERY, null, false))
                    .retrieve()
                    .body(EmbedResponse.class);
            if (response == null || response.embedding() == null) {
                throw new IllegalStateException("Gemini embedding API returned an empty response");
            }
            return normalize(response.embedding().values());
        } catch (RestClientResponseException ex) {
            throw embeddingFailure(ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw embeddingFailure(ex.getMessage());
        }
    }

    private List<float[]> embedBatch(List<String> texts, String documentTitle) {
        List<Map<String, Object>> requests = texts.stream()
                .map(text -> buildEmbedRequest(text, RETRIEVAL_DOCUMENT, documentTitle, true))
                .toList();
        try {
            BatchEmbedResponse response = restClient()
                    .post()
                    .uri("/%s:batchEmbedContents".formatted(modelPath()))
                    .header("x-goog-api-key", aiProperties.getGeminiEmbedding().getApiKey())
                    .body(Map.of("requests", requests))
                    .retrieve()
                    .body(BatchEmbedResponse.class);
            if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
                throw new IllegalStateException("Gemini embedding API returned an incomplete batch");
            }
            return response.embeddings().stream()
                    .map(Embedding::values)
                    .map(this::normalize)
                    .toList();
        } catch (RestClientResponseException ex) {
            throw embeddingFailure(ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw embeddingFailure(ex.getMessage());
        }
    }

    private Map<String, Object> buildEmbedRequest(
            String text,
            String taskType,
            String documentTitle,
            boolean includeModel) {
        Map<String, Object> request = new LinkedHashMap<>();
        if (includeModel) {
            request.put("model", modelPath());
        }
        request.put("content", Map.of("parts", List.of(Map.of("text", text == null ? "" : text.trim()))));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("taskType", taskType);
        config.put("outputDimensionality", outputDimensions());
        config.put("autoTruncate", true);
        if (RETRIEVAL_DOCUMENT.equals(taskType) && StringUtils.hasText(documentTitle)) {
            config.put("title", documentTitle.trim());
        }
        request.put("embedContentConfig", config);
        return request;
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(aiProperties.getGeminiEmbedding().getTimeoutSeconds(), 1));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(aiProperties.getGeminiEmbedding().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini embedding is disabled or GEMINI_API_KEY is not configured");
        }
    }

    private int outputDimensions() {
        return Math.max(128, Math.min(ragProperties.getEmbeddingOutputDimensions(), 3072));
    }

    private String modelPath() {
        String model = ragProperties.getEmbeddingModel();
        if (!StringUtils.hasText(model)) {
            model = "gemini-embedding-001";
        }
        return model.startsWith("models/") ? model : "models/" + model;
    }

    private float[] normalize(float[] values) {
        if (values == null || values.length == 0) {
            return new float[0];
        }
        double normSquared = 0.0;
        for (float value : values) {
            normSquared += value * value;
        }
        if (normSquared == 0.0) {
            return values;
        }
        double norm = Math.sqrt(normSquared);
        float[] normalized = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = (float) (values[i] / norm);
        }
        return normalized;
    }

    private IllegalStateException embeddingFailure(String detail) {
        String message = StringUtils.hasText(detail) ? detail : "Unknown upstream error";
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        return new IllegalStateException("Gemini embedding request failed: " + message);
    }

    private record EmbedResponse(Embedding embedding) {
    }

    private record BatchEmbedResponse(List<Embedding> embeddings) {
    }

    private record Embedding(float[] values) {
    }
}
