package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.Role;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        Role role,
        UserStatus status,
        int failedLoginAttempts,
        Instant lockedUntil,
        boolean passwordResetRequired,
        Instant createdAt
) {

    public static AdminUserResponse from(User user) {
        return from(user, user.getAvatarUrl());
    }

    public static AdminUserResponse from(User user, String avatarUrl) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                avatarUrl,
                user.getRole(),
                user.getStatus(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                user.isPasswordResetRequired(),
                user.getCreatedAt()
        );
    }
}
