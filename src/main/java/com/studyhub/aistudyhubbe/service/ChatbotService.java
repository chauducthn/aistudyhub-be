package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.ChatMessageResponse;
import com.studyhub.aistudyhubbe.dto.ChatRequest;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.entity.ChatMessage;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.ChatMessageRepository;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotService {

    private static final String LOCAL_MODEL_NAME = "LOCAL_STUDY_ASSISTANT";
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<DocumentStatus> EXCLUDED_CHAT_DOCUMENT_STATUSES = List.of(
            DocumentStatus.DELETED,
            DocumentStatus.REMOVED,
            DocumentStatus.LOCKED
    );

    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public ChatbotService(
            ChatMessageRepository chatMessageRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
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
        chatMessage.setResponse(generateStudyResponse(prompt, document));
        chatMessage.setModel(LOCAL_MODEL_NAME);

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

    private String generateStudyResponse(String prompt, Document document) {
        String target = document == null
                ? "your study materials"
                : "the document \"%s\"".formatted(document.getTitle());
        return """
                Here is a focused study response for %s:
                1. Main question: %s
                2. Suggested approach: break the topic into definitions, key ideas, examples, and exam-style questions.
                3. Next step: ask for a summary, a quiz, or a concept explanation to continue.
                """.formatted(target, prompt);
    }
}
