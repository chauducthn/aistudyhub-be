package com.studyhub.aistudyhubbe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubjectRequest(
        @NotBlank(message = "Subject name is required")
        @Size(max = 120, message = "Subject name must be at most 120 characters")
        String name
) {
}
