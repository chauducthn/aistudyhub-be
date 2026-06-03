package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public record AdminDocumentStatusRequest(
        @NotNull(message = "Document status is required")
        DocumentStatus status
) {
}
