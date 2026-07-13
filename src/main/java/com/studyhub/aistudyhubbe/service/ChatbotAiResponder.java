package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.service.GeminiChatClient.GeminiResult;
import com.studyhub.aistudyhubbe.service.rag.RagRetrievalService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatbotAiResponder {

    private final GeminiChatClient geminiChatClient;
    private final OpenAiChatClient openAiChatClient;
    private final AiProperties aiProperties;
    private final RagRetrievalService ragRetrievalService;

    public ChatbotAiResponder(
            GeminiChatClient geminiChatClient,
            OpenAiChatClient openAiChatClient,
            AiProperties aiProperties,
            RagRetrievalService ragRetrievalService) {
        this.geminiChatClient = geminiChatClient;
        this.openAiChatClient = openAiChatClient;
        this.aiProperties = aiProperties;
        this.ragRetrievalService = ragRetrievalService;
    }

    public StudyAiResponse generate(String prompt, Document document) {
        String context = ragRetrievalService.buildContext(document, prompt);
        ensureGeminiProvider();

        GeminiResult geminiResult = geminiChatClient.generate(
                prompt,
                document == null ? null : document.getTitle(),
                context);
        return new StudyAiResponse(geminiResult.response(), geminiResult.model());
    }

    private void ensureGeminiProvider() {
        String provider = normalizedProvider();
        if (!"gemini".equals(provider)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unsupported AI provider: %s. Configure AI_PROVIDER=gemini.".formatted(provider));
        }
        if (!geminiChatClient.isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini API key is not configured");
        }
    }

    private String normalizedProvider() {
        String provider = aiProperties.getProvider();
        if (!StringUtils.hasText(provider)) {
            return "gemini";
        }
        return provider.trim().toLowerCase();
    }

    public record StudyAiResponse(String response, String model) {
    }
}
