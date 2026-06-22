package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.config.RagProperties;
import com.studyhub.aistudyhubbe.exception.ApiException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GeminiEmbeddingClient {

    private static final String RETRIEVAL_DOCUMENT = "RETRIEVAL_DOCUMENT";
    private static final String QUESTION_ANSWERING = "QUESTION_ANSWERING";

    private final AiProperties aiProperties;
    private final RagProperties ragProperties;

    public GeminiEmbeddingClient(AiProperties aiProperties, RagProperties ragProperties) {
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(aiProperties.getGemini().getApiKey());
    }

    public float[] embed(String text) {
        return embed(text, null);
    }

    public float[] embedDocument(String text) {
        return embed(text, RETRIEVAL_DOCUMENT);
    }

    public float[] embedQuestion(String text) {
        return embed(text, QUESTION_ANSWERING);
    }

    private float[] embed(String text, String taskType) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini API key is not configured");
        }
        if (!StringUtils.hasText(text)) {
            return new float[0];
        }

        try {
            EmbedResponse response = restClient()
                    .post()
                    .uri("/%s:embedContent?key={apiKey}".formatted(modelPath()), aiProperties.getGemini().getApiKey())
                    .body(buildRequestBody(text.trim(), taskType))
                    .retrieve()
                    .body(EmbedResponse.class);

            if (response == null || response.embedding() == null || response.embedding().values() == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini embedding API returned an empty vector");
            }
            return response.embedding().values();
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini embedding failed: " + shorten(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini embedding failed: " + shorten(ex.getMessage()));
        }
    }

    private Map<String, Object> buildRequestBody(String text, String taskType) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (supportsTaskType() && StringUtils.hasText(taskType)) {
            body.put("taskType", taskType);
        }
        body.put("content", Map.of("parts", List.of(Map.of("text", text))));
        return body;
    }

    private boolean supportsTaskType() {
        return "gemini-embedding-001".equals(ragProperties.getEmbeddingModel());
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(aiProperties.getGemini().getTimeoutSeconds(), 1));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(aiProperties.getGemini().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private String modelPath() {
        String model = ragProperties.getEmbeddingModel();
        return model.startsWith("models/") ? model : "models/" + model;
    }

    private String shorten(String message) {
        if (!StringUtils.hasText(message)) {
            return "Unknown error";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private record EmbedResponse(Embedding embedding) {
    }

    private record Embedding(float[] values) {
    }
}
