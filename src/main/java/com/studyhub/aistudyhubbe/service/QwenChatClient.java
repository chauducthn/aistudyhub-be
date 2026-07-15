package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.exception.ApiException;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class QwenChatClient {

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

    public QwenChatClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(aiProperties.getQwen().getApiKey());
    }

    public QwenResult generate(String prompt, String documentTitle, String documentContext) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Qwen API key is not configured");
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

        QwenChatRequest request = new QwenChatRequest(
                aiProperties.getQwen().getModel(),
                List.of(
                        new QwenChatRequest.Message("system", SYSTEM_INSTRUCTION),
                        new QwenChatRequest.Message("user", userMessage)
                ),
                aiProperties.getQwen().getTemperature(),
                aiProperties.getQwen().getMaxOutputTokens(),
                false
        );

        try {
            QwenChatResponse response = restClient()
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + aiProperties.getQwen().getApiKey())
                    .body(request)
                    .retrieve()
                    .body(QwenChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API returned an empty response");
            }

            String text = response.choices().get(0).message().content();
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API returned an empty text content");
            }

            return new QwenResult(text.trim(), aiProperties.getQwen().getModel());
        } catch (RestClientResponseException ex) {
            throw qwenApiException(ex);
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API is temporarily unavailable. Please try again later.");
        }
    }

    private ApiException qwenApiException(RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        if (statusCode == 429) {
            return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Qwen quota or rate limit has been reached.");
        }
        if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
            return new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Qwen API key, endpoint, or model configuration is invalid. Please check QWEN_* settings.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API returned error: " + ex.getResponseBodyAsString());
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(aiProperties.getQwen().getTimeoutSeconds(), 1));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(aiProperties.getQwen().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public record QwenResult(String response, String model) {
    }

    public record QwenChatRequest(
            String model,
            List<Message> messages,
            double temperature,
            Integer max_tokens,
            Boolean stream
    ) {
        public record Message(String role, String content) {
        }
    }

    public record QwenChatResponse(List<Choice> choices) {
        public record Choice(Message message) {
        }

        public record Message(String content) {
        }
    }
}
