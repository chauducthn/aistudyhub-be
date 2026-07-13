package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.service.gemini.GeminiChatRequestBuilder;
import com.studyhub.aistudyhubbe.service.gemini.GeminiChatResponseParser;
import com.studyhub.aistudyhubbe.service.gemini.GeminiChatResponseParser.GeminiGenerateResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GeminiChatClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiChatClient.class);

    private final AiProperties aiProperties;
    private final GeminiChatRequestBuilder requestBuilder;
    private final GeminiChatResponseParser responseParser;

    public GeminiChatClient(
            AiProperties aiProperties,
            GeminiChatRequestBuilder requestBuilder,
            GeminiChatResponseParser responseParser) {
        this.aiProperties = aiProperties;
        this.requestBuilder = requestBuilder;
        this.responseParser = responseParser;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(aiProperties.getGemini().getApiKey());
    }

    public String modelName() {
        return aiProperties.getGemini().getModel();
    }

    public GeminiResult generate(String prompt, String documentTitle, String documentContext) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini API key is not configured");
        }

        List<String> modelCandidates = aiProperties.getGeminiModelCandidates();
        ApiException lastFailure = null;

        for (String model : modelCandidates) {
            try {
                return generateWithModel(model, prompt, documentTitle, documentContext);
            } catch (ApiException ex) {
                if (!shouldTryNextModel(ex, modelCandidates, model)) {
                    throw ex;
                }
                lastFailure = ex;
                LOGGER.warn("Gemini model {} failed, trying next model. Reason: {}", model, ex.getMessage());
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "No Gemini model candidates are configured");
    }

    private GeminiResult generateWithModel(
            String model,
            String prompt,
            String documentTitle,
            String documentContext) {
        try {
            GeminiGenerateResponse response = restClient()
                    .post()
                    .uri("/%s:generateContent".formatted(requestBuilder.modelPath(model)))
                    .header("x-goog-api-key", aiProperties.getGemini().getApiKey())
                    .body(requestBuilder.build(prompt, documentTitle, documentContext))
                    .retrieve()
                    .body(GeminiGenerateResponse.class);

            String text = responseParser.extractText(response);
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini API returned an empty response");
            }

            return new GeminiResult(text.trim(), normalizeModelLabel(model));
        } catch (RestClientResponseException ex) {
            throw geminiApiException(ex);
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini is temporarily unavailable. Please try again later.");
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return message.contains("quota")
                || message.contains("429")
                || message.contains("503")
                || message.contains("resource_exhausted")
                || message.contains("high demand")
                || message.contains("rate limit")
                || message.contains("not found")
                || message.contains("404")
                || message.contains("model")
                || message.contains("invalid");
    }

    private String normalizeModelLabel(String model) {
        return model.startsWith("models/") ? model.substring("models/".length()) : model;
    }

    private ApiException geminiApiException(RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        if (statusCode == 429) {
            return new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Gemini quota or rate limit has been reached. Please try again later or use another API key.");
        }
        if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
            return new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Gemini API key or model configuration is invalid. Please check backend AI settings.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "Gemini is temporarily unavailable. Please try again later.");
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

    public record GeminiResult(String response, String model) {
    }
}
