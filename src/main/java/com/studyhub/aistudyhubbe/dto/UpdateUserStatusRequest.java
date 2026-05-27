package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Status is required")
        UserStatus status
) {
}
