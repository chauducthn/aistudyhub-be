package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.ChatSession;
import java.time.Instant;

public record ChatSessionResponse(
        Long id,
        String title,
        Instant createdAt
) {
    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt()
        );
    }
}
