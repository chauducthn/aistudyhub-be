package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.service.QwenChatClient.QwenResult;
import com.studyhub.aistudyhubbe.service.rag.ConversationAwareRetrievalQueryBuilder;
import com.studyhub.aistudyhubbe.service.rag.RagRetrievalService;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatbotAiResponder {

    private final OpenAiChatClient openAiChatClient;
    private final QwenChatClient qwenChatClient;
    private final AiProperties aiProperties;
    private final RagRetrievalService ragRetrievalService;
    private final ConversationAwareRetrievalQueryBuilder retrievalQueryBuilder;
    private final ChatSafetyGuard chatSafetyGuard;

    public ChatbotAiResponder(
            OpenAiChatClient openAiChatClient,
            QwenChatClient qwenChatClient,
            AiProperties aiProperties,
            RagRetrievalService ragRetrievalService,
            ConversationAwareRetrievalQueryBuilder retrievalQueryBuilder,
            ChatSafetyGuard chatSafetyGuard) {
        this.openAiChatClient = openAiChatClient;
        this.qwenChatClient = qwenChatClient;
        this.aiProperties = aiProperties;
        this.ragRetrievalService = ragRetrievalService;
        this.retrievalQueryBuilder = retrievalQueryBuilder;
        this.chatSafetyGuard = chatSafetyGuard;
    }

    public StudyAiResponse generate(String prompt, Document document, String conversationHistory) {
        Optional<String> guardedResponse = chatSafetyGuard.responseFor(prompt);
        if (guardedResponse.isPresent()) {
            return new StudyAiResponse(guardedResponse.get(), aiProperties.getQwen().getModel());
        }

        String retrievalQuery = retrievalQueryBuilder.build(prompt, conversationHistory);
        String context = ragRetrievalService.buildContext(document, retrievalQuery);
        if (!qwenChatClient.isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Qwen API key is not configured");
        }
        QwenResult qwenResult = qwenChatClient.generate(
                prompt,
                document == null ? null : document.getTitle(),
                context,
                conversationHistory);
        return new StudyAiResponse(qwenResult.response(), qwenResult.model());
    }

    public String performOcr(byte[] imageBytes) {
        if (!openAiChatClient.isConfigured()) {
            return "";
        }
        return openAiChatClient.performOcr(imageBytes);
    }

    public record StudyAiResponse(String response, String model) {
    }
}
