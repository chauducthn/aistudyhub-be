package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.ChatMessageResponse;
import com.studyhub.aistudyhubbe.dto.ChatRequest;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.config.AiProperties;
import com.studyhub.aistudyhubbe.entity.ChatMessage;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.ChatMessageRepository;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import com.studyhub.aistudyhubbe.service.GeminiChatClient.GeminiResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatbotService.class);
    private static final String LOCAL_MODEL_NAME = "LOCAL_STUDY_ASSISTANT";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DOCUMENT_CONTEXT_LENGTH = 12_000;
    private static final List<DocumentStatus> EXCLUDED_CHAT_DOCUMENT_STATUSES = List.of(
            DocumentStatus.DELETED,
            DocumentStatus.REMOVED,
            DocumentStatus.LOCKED
    );

    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final GeminiChatClient geminiChatClient;
    private final AiProperties aiProperties;

    public ChatbotService(
            ChatMessageRepository chatMessageRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            GeminiChatClient geminiChatClient,
            AiProperties aiProperties) {
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.geminiChatClient = geminiChatClient;
        this.aiProperties = aiProperties;
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long userId, ChatRequest request) {
        User user = findUser(userId);
        String prompt = normalizePrompt(request.message());
        Document document = findAccessibleDocument(userId, request.documentId());

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUser(user);
        chatMessage.setDocument(document);
        chatMessage.setPrompt(prompt);
        StudyAiResponse aiResponse = generateStudyResponse(prompt, document);
        chatMessage.setResponse(aiResponse.response());
        chatMessage.setModel(aiResponse.model());

        return ChatMessageResponse.from(chatMessageRepository.save(chatMessage));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> listHistory(Long userId, int page, int size) {
        findUser(userId);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ChatMessageResponse> messages = chatMessageRepository.findByUserIdAndVisibleToUserTrue(userId, pageable)
                .map(ChatMessageResponse::from);
        return PageResponse.from(messages);
    }

    @Transactional
    public void clearHistory(Long userId) {
        findUser(userId);
        chatMessageRepository.hideByUserId(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Document findAccessibleDocument(Long userId, Long documentId) {
        if (documentId == null) {
            return null;
        }

        return documentRepository.findDownloadableById(
                        documentId,
                        userId,
                        DocumentStatus.PUBLIC,
                        EXCLUDED_CHAT_DOCUMENT_STATUSES)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private String normalizePrompt(String prompt) {
        if (prompt == null || prompt.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chat message is required");
        }

        return prompt.trim();
    }

    private StudyAiResponse generateStudyResponse(String prompt, Document document) {
        String target = document == null
                ? "your study materials"
                : "the document \"%s\"".formatted(document.getTitle());

        String context = buildDocumentContext(document);
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
        if (provider == null || provider.trim().isBlank()) {
            return "auto";
        }
        return provider.trim().toLowerCase();
    }

    private String buildDocumentContext(Document document) {
        if (document == null) {
            return "No specific document was selected.";
        }

        if (document.getExtractionStatus() != DocumentExtractionStatus.EXTRACTED) {
            return "Text is not available yet for this document. Extraction status: "
                    + document.getExtractionStatus();
        }

        String extractedText = document.getExtractedText();
        if (extractedText == null || extractedText.isBlank()) {
            return "No readable text was extracted from this document.";
        }

        String normalizedText = extractedText.trim();
        if (normalizedText.length() > MAX_DOCUMENT_CONTEXT_LENGTH) {
            return normalizedText.substring(0, MAX_DOCUMENT_CONTEXT_LENGTH);
        }
        return normalizedText;
    }

    private record StudyAiResponse(String response, String model) {
    }
}
