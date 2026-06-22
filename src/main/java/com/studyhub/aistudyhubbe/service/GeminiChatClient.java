package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.service.gemini.GeminiChatRequestBuilder;
import com.studyhub.aistudyhubbe.service.gemini.GeminiChatResponseParser;
import com.studyhub.aistudyhubbe.service.gemini.GeminiChatResponseParser.GeminiGenerateResponse;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GeminiChatClient {

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

        try {
            GeminiGenerateResponse response = restClient()
                    .post()
                    .uri("/%s:generateContent?key={apiKey}".formatted(requestBuilder.modelPath()), aiProperties.getGemini().getApiKey())
                    .body(requestBuilder.build(prompt, documentTitle, documentContext))
                    .retrieve()
                    .body(GeminiGenerateResponse.class);

            String text = responseParser.extractText(response);
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini API returned an empty response");
            }

            return new GeminiResult(text.trim(), modelName());
        } catch (RestClientResponseException ex) {
            throw geminiApiException(ex);
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini is temporarily unavailable. Please try again later.");
        }
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
