package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.ChatMessage;
import com.studyhub.aistudyhubbe.entity.Document;
import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long userId,
        Long documentId,
        String documentTitle,
        Long sessionId,
        String message,
        String response,
        String model,
        Instant createdAt
) {

    public static ChatMessageResponse from(ChatMessage chatMessage) {
        Document document = chatMessage.getDocument();
        return new ChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getUser().getId(),
                document == null ? null : document.getId(),
                document == null ? null : document.getTitle(),
                chatMessage.getChatSession() == null ? null : chatMessage.getChatSession().getId(),
                chatMessage.getPrompt(),
                chatMessage.getResponse(),
                chatMessage.getModel(),
                chatMessage.getCreatedAt()
        );
    }
}
