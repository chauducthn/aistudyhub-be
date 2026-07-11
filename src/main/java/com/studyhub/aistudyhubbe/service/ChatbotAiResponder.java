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
        String provider = normalizedProvider();

        try {
            if ("openai".equals(provider)) {
                return generateWithOpenAi(prompt, document, context);
            }
            if ("gemini".equals(provider) || "auto".equals(provider)) {
                if (shouldUseGemini()) {
                    return generateWithGemini(prompt, document, context);
                }
                if ("gemini".equals(provider)) {
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini API key is not configured");
                }
            } else if (!"local".equals(provider)) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unsupported AI provider: %s. Use auto, gemini, openai, or local.".formatted(provider));
            }
        } catch (ApiException ex) {
            if (canFallbackAfterProviderFailure()) {
                LOGGER.warn("AI provider {} failed. Falling back to local study assistant. Reason: {}",
                        provider, ex.getMessage());
                return buildLocalFallback(prompt, context, ex.getMessage());
            }
            throw ex;
        }

        return buildLocalFallback(prompt, context, null);
    }

    public String performOcr(byte[] imageBytes) {
        String provider = normalizedProvider();
        if ("openai".equals(provider)) {
            return openAiChatClient.performOcr(imageBytes);
        }
        return geminiChatClient.performOcr(imageBytes);
    }

    private StudyAiResponse generateWithOpenAi(String prompt, Document document, String context) {
        if (!openAiChatClient.isConfigured()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OpenAI API key is not configured");
        }
        OpenAiChatClient.OpenAiResult result = openAiChatClient.generate(
                prompt,
                document == null ? null : document.getTitle(),
                context);
        return new StudyAiResponse(result.response(), result.model());
    }

    private StudyAiResponse generateWithGemini(String prompt, Document document, String context) {
        GeminiResult geminiResult = geminiChatClient.generate(
                prompt,
                document == null ? null : document.getTitle(),
                context);
        return new StudyAiResponse(geminiResult.response(), geminiResult.model());
    }

    private StudyAiResponse buildLocalFallback(String prompt, String context, String failureReason) {
        if (looksLikeSmallTalk(prompt)) {
            if (StringUtils.hasText(failureReason)) {
                return new StudyAiResponse(
                        """
                        Chào bạn! Mình vẫn ổn.

                        Hiện **Gemini/OpenAI** đang tạm không khả dụng. Bạn thử lại sau, hoặc kiểm tra API key/quota rồi khởi động lại backend.

                        Khi AI hoạt động, mình có thể giúp bạn học tập, tóm tắt tài liệu và tạo câu hỏi ôn tập.
                        """,
                        LOCAL_MODEL_NAME);
            }
            return new StudyAiResponse(
                    "Chào bạn! Mình là AI Study Hub. Bạn có thể hỏi về bài học, nhờ tóm tắt tài liệu, hoặc tạo quiz. "
                            + "Chọn một document ở trên nếu muốn hỏi theo tài liệu cụ thể.",
                    LOCAL_MODEL_NAME);
        }

        if (StringUtils.hasText(failureReason)) {
            return new StudyAiResponse("""
                    **AI Study Assistant is temporarily unavailable.**

                    Reason: %s

                    Please verify your API key and quota, then restart the backend.

                    Your question: %s
                    """.formatted(shorten(failureReason), prompt.trim()), LOCAL_MODEL_NAME);
        }

        String contextLine = StringUtils.hasText(context) && !context.contains("No document")
                ? context
                : "No document context selected yet.";

        return new StudyAiResponse("""
                I can help with this topic once Gemini or OpenAI is connected.

                **Your question:** %s

                **Context:** %s

                Try selecting one of your uploaded documents, or ask for a summary, quiz, or concept explanation.
                """.formatted(prompt.trim(), contextLine), LOCAL_MODEL_NAME);
    }

    private boolean looksLikeSmallTalk(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return false;
        }
        String normalized = prompt.trim().toLowerCase();
        return normalized.matches("^(hi|hello|hey|chao|xin chao|bạn khoẻ không|ban khoe khong|ok|thanks|thank you|cảm ơn).*");
    }

    private String shorten(String message) {
        if (!StringUtils.hasText(message)) {
            return "Unknown error";
        }
        return message.length() > 220 ? message.substring(0, 220) + "..." : message;
    }

    private boolean shouldUseGemini() {
        return geminiChatClient.isConfigured();
    }

    private boolean canFallbackAfterProviderFailure() {
        return aiProperties.isFallbackToLocal()
                && ("auto".equals(normalizedProvider()) || "local".equals(normalizedProvider()));
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
