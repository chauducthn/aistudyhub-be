package com.studyhub.aistudyhubbe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        Long documentId,
        Long sessionId,

        @NotBlank(message = "Chat message is required")
        @Size(max = 4000, message = "Chat message must not exceed 4000 characters")
        String message
) {
}
