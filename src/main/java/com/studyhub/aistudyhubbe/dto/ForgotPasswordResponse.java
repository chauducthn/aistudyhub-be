package com.studyhub.aistudyhubbe.dto;

public record ForgotPasswordResponse(
        String message,
        String resetToken
) {
}
