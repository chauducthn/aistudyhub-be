package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.exception.ApiException;
import java.time.Duration;
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
public class GeminiChatClient {

    private static final String SYSTEM_INSTRUCTION = """
            You are AI Study Hub, an academic AI assistant.
            Answer clearly, accurately, and in the same language as the user's question unless the user asks otherwise.
            Prefer the provided document context when it is available.
            If the context is missing or insufficient, say what is missing and give a useful general study explanation.
            Do not invent citations or claim that a document contains information that is not in the provided context.
            """;

    private final AiProperties aiProperties;

    public GeminiChatClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
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
                    .uri("/%s:generateContent?key={apiKey}".formatted(normalizedModelPath()), aiProperties.getGemini().getApiKey())
                    .body(buildRequest(prompt, documentTitle, documentContext))
                    .retrieve()
                    .body(GeminiGenerateResponse.class);

            String text = extractText(response);
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini API returned an empty response");
            }

            return new GeminiResult(text.trim(), modelName());
        } catch (RestClientResponseException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Gemini API request failed: " + shorten(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini API request failed: " + shorten(ex.getMessage()));
        }
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

    private Map<String, Object> buildRequest(String prompt, String documentTitle, String documentContext) {
        String userMessage = """
                User question:
                %s

                Selected document:
                %s

                Document context:
                %s

                Please provide a focused study answer. When helpful, include bullet points, definitions, examples, or quiz questions.
                """.formatted(
                prompt,
                StringUtils.hasText(documentTitle) ? documentTitle : "No document selected",
                StringUtils.hasText(documentContext) ? documentContext : "No document context available");

        return Map.of(
                "systemInstruction", content(SYSTEM_INSTRUCTION),
                "contents", List.of(content(userMessage)),
                "generationConfig", Map.of(
                        "temperature", aiProperties.getGemini().getTemperature(),
                        "maxOutputTokens", aiProperties.getGemini().getMaxOutputTokens()
                )
        );
    }

    private Map<String, Object> content(String text) {
        return Map.of("parts", List.of(Map.of("text", text)));
    }

    private String normalizedModelPath() {
        String model = aiProperties.getGemini().getModel();
        if (!StringUtils.hasText(model)) {
            return "models/gemini-2.0-flash";
        }
        String trimmedModel = model.trim();
        return trimmedModel.startsWith("models/") ? trimmedModel : "models/" + trimmedModel;
    }

    private String extractText(GeminiGenerateResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return "";
        }

        Candidate firstCandidate = response.candidates().getFirst();
        if (firstCandidate.content() == null || firstCandidate.content().parts() == null) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (Part part : firstCandidate.content().parts()) {
            if (StringUtils.hasText(part.text())) {
                if (!text.isEmpty()) {
                    text.append(System.lineSeparator());
                }
                text.append(part.text().trim());
            }
        }
        return text.toString();
    }

    private String shorten(String message) {
        if (!StringUtils.hasText(message)) {
            return "Unknown error";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    public record GeminiResult(String response, String model) {
    }

    private record GeminiGenerateResponse(List<Candidate> candidates) {
    }

    private record Candidate(Content content, String finishReason) {
    }

    private record Content(List<Part> parts, String role) {
    }

    private record Part(String text) {
    }
}
