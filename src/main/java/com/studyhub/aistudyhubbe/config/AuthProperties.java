package com.studyhub.aistudyhubbe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private int maxFailedAttempts = 5;
    private int lockDurationMinutes = 15;
    private int passwordResetExpirationMinutes = 30;
    /** Dev only: return reset token in API response when email service is not configured */
    private boolean exposeResetTokenInResponse = false;
    private String passwordResetFrontendUrl = "http://localhost:5173/reset-password";

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public int getLockDurationMinutes() {
        return lockDurationMinutes;
    }

    public void setLockDurationMinutes(int lockDurationMinutes) {
        this.lockDurationMinutes = lockDurationMinutes;
    }

    public int getPasswordResetExpirationMinutes() {
        return passwordResetExpirationMinutes;
    }

    public void setPasswordResetExpirationMinutes(int passwordResetExpirationMinutes) {
        this.passwordResetExpirationMinutes = passwordResetExpirationMinutes;
    }

    public boolean isExposeResetTokenInResponse() {
        return exposeResetTokenInResponse;
    }

    public void setExposeResetTokenInResponse(boolean exposeResetTokenInResponse) {
        this.exposeResetTokenInResponse = exposeResetTokenInResponse;
    }

    public String getPasswordResetFrontendUrl() {
        return passwordResetFrontendUrl;
    }

    public void setPasswordResetFrontendUrl(String passwordResetFrontendUrl) {
        this.passwordResetFrontendUrl = passwordResetFrontendUrl;
    }
}
