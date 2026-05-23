package com.studyhub.aistudyhubbe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        String avatarUrl
) {
}
