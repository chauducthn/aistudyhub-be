package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.exception.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class QwenChatClient {

    private static final Duration DEFAULT_RATE_LIMIT_RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration MAX_RATE_LIMIT_RETRY_DELAY = Duration.ofSeconds(20);
    private static final int MAX_UPSTREAM_ERROR_LENGTH = 500;

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
            QwenChatResponse response = executeWithRateLimitRetry(request);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API returned an empty response");
            }

            String text = response.choices().get(0).message().content();
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API returned an empty text content");
            }

            return new QwenResult(text.trim(), aiProperties.getQwen().getModel());
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API is temporarily unavailable. Please try again later.");
        }
    }

    private QwenChatResponse executeWithRateLimitRetry(QwenChatRequest request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return executeRequest(request);
            } catch (RestClientResponseException ex) {
                boolean shouldRetry = attempt == 0 && ex.getStatusCode().value() == 429;
                if (!shouldRetry) {
                    throw qwenApiException(ex);
                }
                waitBeforeRateLimitRetry(ex);
            }
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API retry failed unexpectedly.");
    }

    private QwenChatResponse executeRequest(QwenChatRequest request) {
        return restClient()
                .post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + aiProperties.getQwen().getApiKey())
                .body(request)
                .retrieve()
                .body(QwenChatResponse.class);
    }

    private void waitBeforeRateLimitRetry(RestClientResponseException ex) {
        String retryAfter = ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        Duration delay = parseRetryDelay(retryAfter, Instant.now());
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Qwen rate-limit retry was interrupted. Please try again.");
        }
    }

    static Duration parseRetryDelay(String retryAfter, Instant now) {
        if (!StringUtils.hasText(retryAfter)) {
            return DEFAULT_RATE_LIMIT_RETRY_DELAY;
        }

        Duration requestedDelay;
        try {
            requestedDelay = Duration.ofSeconds(Math.max(Long.parseLong(retryAfter.trim()), 0));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime
                        .parse(retryAfter.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant();
                requestedDelay = Duration.between(now, retryAt);
                if (requestedDelay.isNegative()) {
                    requestedDelay = Duration.ZERO;
                }
            } catch (DateTimeParseException invalidDate) {
                requestedDelay = DEFAULT_RATE_LIMIT_RETRY_DELAY;
            }
        }

        return requestedDelay.compareTo(MAX_RATE_LIMIT_RETRY_DELAY) > 0
                ? MAX_RATE_LIMIT_RETRY_DELAY
                : requestedDelay;
    }

    private ApiException qwenApiException(RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        if (statusCode == 429) {
            return new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Qwen/OpenRouter is temporarily rate limited after one retry. " + upstreamErrorDetail(ex));
        }
        if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
            return new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Qwen API key, endpoint, or model configuration is invalid. Please check QWEN_* settings.");
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "Qwen API returned error: " + ex.getResponseBodyAsString());
    }

    private String upstreamErrorDetail(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            String retryAfter = ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            return StringUtils.hasText(retryAfter)
                    ? "OpenRouter requested another retry after " + retryAfter.trim() + " seconds."
                    : "OpenRouter did not provide an error body.";
        }

        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() > MAX_UPSTREAM_ERROR_LENGTH) {
            normalized = normalized.substring(0, MAX_UPSTREAM_ERROR_LENGTH) + "...";
        }
        return "OpenRouter response: " + normalized;
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
