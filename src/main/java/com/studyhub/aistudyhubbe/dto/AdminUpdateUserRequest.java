package com.studyhub.aistudyhubbe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        @Size(max = 32, message = "Phone must not exceed 32 characters")
        String phone
) {
}
