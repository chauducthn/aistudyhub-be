package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.ChatMessageResponse;
import com.studyhub.aistudyhubbe.dto.ChatRequest;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.entity.ChatMessage;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.ChatMessageRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import com.studyhub.aistudyhubbe.service.ChatbotAiResponder.StudyAiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatbotDocumentAccess chatbotDocumentAccess;
    private final ChatbotAiResponder chatbotAiResponder;

    public ChatbotService(
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            ChatbotDocumentAccess chatbotDocumentAccess,
            ChatbotAiResponder chatbotAiResponder) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.chatbotDocumentAccess = chatbotDocumentAccess;
        this.chatbotAiResponder = chatbotAiResponder;
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long userId, ChatRequest request) {
        User user = findUser(userId);
        String prompt = normalizePrompt(request.message());
        Document document = chatbotDocumentAccess.findAccessibleDocument(userId, request.documentId());

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUser(user);
        chatMessage.setDocument(document);
        chatMessage.setPrompt(prompt);

        StudyAiResponse aiResponse = chatbotAiResponder.generate(prompt, document);
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

    private String normalizePrompt(String prompt) {
        if (prompt == null || prompt.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chat message is required");
        }
        return prompt.trim();
    }
}
