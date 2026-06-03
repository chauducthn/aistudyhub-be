package com.studyhub.aistudyhubbe.dto;

import jakarta.validation.constraints.Size;

public record DocumentUpdateRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        Long subjectId
) {
}
