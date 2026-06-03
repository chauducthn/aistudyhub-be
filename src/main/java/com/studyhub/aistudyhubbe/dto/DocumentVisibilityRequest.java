package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public record DocumentVisibilityRequest(
        @NotNull(message = "Status is required")
        DocumentStatus status
) {
}
