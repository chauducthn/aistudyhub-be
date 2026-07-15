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
public class OpenAiChatClient {

    private static final String SYSTEM_INSTRUCTION = """
            You are AI Study Hub, an academic AI assistant.
            Answer clearly, accurately, and in the same language as the user's question unless the user asks otherwise.
            When a document is selected, use the provided excerpts as grounding and reason from them to answer the user's specific question.
            Do not summarize the whole document unless the user explicitly asks for a summary.
            Do not repeat the excerpts verbatim. Synthesize, explain implications, and connect ideas when the context supports it.
            If the excerpts are only representative background, infer cautiously and say when the document is insufficient.
            Do not invent facts or claim that a document contains information that is not supported by the provided context.
            """;

    private final AiProperties aiProperties;

    public OpenAiChatClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(aiProperties.getOpenai().getApiKey());
    }

    public String modelName() {
        return aiProperties.getOpenai().getModel();
    }

    public OpenAiResult generate(String prompt, String documentTitle, String documentContext) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OpenAI API key is not configured");
        }

        String userMessage = """
                User question:
                %s

                Selected document:
                %s

                Document context:
                %s

                Answer the user question directly and thoughtfully. Use short paragraphs or bullets when helpful.
                Keep the answer complete; do not stop mid-sentence. If the answer is long, prefer a concise complete summary.
                If the document context is insufficient, say exactly what is missing instead of forcing an answer.
                """.formatted(
                prompt,
                StringUtils.hasText(documentTitle) ? documentTitle : "No document selected",
                StringUtils.hasText(documentContext) ? documentContext : "No document context available");

        OpenAiChatRequest request = new OpenAiChatRequest(
                aiProperties.getOpenai().getModel(),
                List.of(
                        new OpenAiChatRequest.Message("system", SYSTEM_INSTRUCTION),
                        new OpenAiChatRequest.Message("user", userMessage)
                ),
                aiProperties.getOpenai().getTemperature(),
                aiProperties.getOpenai().getMaxOutputTokens(),
                false
        );

        try {
            OpenAiChatResponse response = restClient()
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + aiProperties.getOpenai().getApiKey())
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI API returned an empty response");
            }

            String text = response.choices().get(0).message().content();
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI API returned an empty text content");
            }

            return new OpenAiResult(text.trim(), modelName());
        } catch (RestClientResponseException ex) {
            throw openAiApiException(ex);
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI API is temporarily unavailable. Please try again later.");
        }
    }

    private ApiException openAiApiException(RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        if (statusCode == 429) {
            return new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "OpenAI rate limit or quota has been reached. Please try again later.");
        }
        if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
            return new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenAI API key or model configuration is invalid. Please check backend settings.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI API returned error: " + ex.getResponseBodyAsString());
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(aiProperties.getOpenai().getTimeoutSeconds(), 1));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(aiProperties.getOpenai().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public record OpenAiResult(String response, String model) {}

    public record OpenAiChatRequest(
            String model,
            List<Message> messages,
            double temperature,
            Integer max_tokens,
            Boolean stream
    ) {
        public record Message(String role, String content) {}
    }

    public record OpenAiChatResponse(List<Choice> choices) {
        public record Choice(Message message) {}
        public record Message(String content) {}
    }

    public String performOcr(byte[] imageBytes) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OpenAI API key is not configured");
        }

        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:image/png;base64," + base64Image;

        List<Map<String, Object>> messages = List.of(
            Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "text", "text", "Extract all readable text from this document image page. Output only the plain text found in the document. Do not summarize, explain or translate it. Keep the original language."),
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                )
            )
        );

        Map<String, Object> requestBody = Map.of(
            "model", aiProperties.getOpenai().getModel(),
            "messages", messages,
            "max_tokens", 2048,
            "stream", false
        );

        try {
            OpenAiChatResponse response = restClient()
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + aiProperties.getOpenai().getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return "";
            }
            return response.choices().get(0).message().content();
        } catch (Exception ex) {
            return "";
        }
    }
}
