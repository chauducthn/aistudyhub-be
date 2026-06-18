package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.service.GeminiChatClient.GeminiResult;
import com.studyhub.aistudyhubbe.service.rag.RagRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatbotAiResponder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatbotAiResponder.class);
    private static final String LOCAL_MODEL_NAME = "LOCAL_STUDY_ASSISTANT";

    private final GeminiChatClient geminiChatClient;
    private final AiProperties aiProperties;
    private final RagRetrievalService ragRetrievalService;

    public ChatbotAiResponder(
            GeminiChatClient geminiChatClient,
            AiProperties aiProperties,
            RagRetrievalService ragRetrievalService) {
        this.geminiChatClient = geminiChatClient;
        this.aiProperties = aiProperties;
        this.ragRetrievalService = ragRetrievalService;
    }

    public StudyAiResponse generate(String prompt, Document document) {
        String target = document == null
                ? "your study materials"
                : "the document \"%s\"".formatted(document.getTitle());
        String context = ragRetrievalService.buildContext(document, prompt);

        if (shouldUseGemini()) {
            try {
                GeminiResult geminiResult = geminiChatClient.generate(
                        prompt,
                        document == null ? null : document.getTitle(),
                        context);
                return new StudyAiResponse(geminiResult.response(), geminiResult.model());
            } catch (ApiException ex) {
                if (!canFallbackAfterGeminiFailure()) {
                    throw ex;
                }
                LOGGER.warn("Gemini request failed. Falling back to local study assistant. Reason: {}", ex.getMessage());
            }
        }

        return new StudyAiResponse("""
                Here is a focused study response for %s:
                1. Main question: %s
                2. Document context: %s
                3. Suggested approach: break the topic into definitions, key ideas, examples, and exam-style questions.
                4. Next step: ask for a summary, a quiz, or a concept explanation to continue.
                """.formatted(target, prompt, context), LOCAL_MODEL_NAME);
    }

    private boolean shouldUseGemini() {
        String provider = normalizedProvider();
        if ("local".equals(provider)) {
            return false;
        }
        if ("gemini".equals(provider)) {
            if (!geminiChatClient.isConfigured()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini API key is not configured");
            }
            return true;
        }
        return geminiChatClient.isConfigured();
    }

    private boolean canFallbackAfterGeminiFailure() {
        return "auto".equals(normalizedProvider()) && aiProperties.isFallbackToLocal();
    }

    private String normalizedProvider() {
        String provider = aiProperties.getProvider();
        if (!StringUtils.hasText(provider)) {
            return "auto";
        }
        return provider.trim().toLowerCase();
    }

    public record StudyAiResponse(String response, String model) {
    }
}
