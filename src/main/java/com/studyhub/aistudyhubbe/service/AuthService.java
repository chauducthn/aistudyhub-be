package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AuthProperties;
import com.studyhub.aistudyhubbe.config.JwtProperties;
import com.studyhub.aistudyhubbe.dto.AuthResponse;
import com.studyhub.aistudyhubbe.dto.ForgotPasswordResponse;
import com.studyhub.aistudyhubbe.dto.LoginRequest;
import com.studyhub.aistudyhubbe.dto.RegisterRequest;
import com.studyhub.aistudyhubbe.dto.ResetPasswordRequest;
import com.studyhub.aistudyhubbe.dto.UserResponse;
import com.studyhub.aistudyhubbe.entity.PasswordResetToken;
import com.studyhub.aistudyhubbe.entity.RefreshToken;
import com.studyhub.aistudyhubbe.entity.Role;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.PasswordResetTokenRepository;
import com.studyhub.aistudyhubbe.repository.RefreshTokenRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_RESET_MESSAGE =
            "If the email exists, password reset instructions have been sent.";

    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authProperties = authProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        ensureAccountNotLocked(user);

        if (passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            resetFailedAttempts(user);
            refreshTokenRepository.revokeAllByUserId(user.getId());
            return buildAuthResponse(user);
        }

        handleFailedLogin(user);
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.revokeByToken(refreshTokenValue);
        }
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenValue)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        User user = refreshToken.getUser();
        ensureAccountNotLocked(user);

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return buildAuthResponse(user);
    }

    @Transactional
    public ForgotPasswordResponse requestPasswordReset(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        var userOptional = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (userOptional.isEmpty()) {
            return new ForgotPasswordResponse(GENERIC_RESET_MESSAGE, null);
        }

        User user = userOptional.get();
        passwordResetTokenRepository.invalidateAllByUserId(user.getId());

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setExpiresAt(Instant.now().plusSeconds(
                (long) authProperties.getPasswordResetExpirationMinutes() * 60));
        passwordResetTokenRepository.save(resetToken);

        String resetLink = authProperties.getPasswordResetFrontendUrl()
                + "?token="
                + resetToken.getToken();
        log.info("Password reset requested for {} — link: {}", normalizedEmail, resetLink);

        String exposedToken = authProperties.isExposeResetTokenInResponse()
                ? resetToken.getToken()
                : null;

        return new ForgotPasswordResponse(GENERIC_RESET_MESSAGE, exposedToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenAndUsedFalse(request.token().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.invalidateAllByUserId(user.getId());
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    @Transactional
    public String createRefreshToken(Long userId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(userRepository.getReferenceById(userId));
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(Instant.now().plusSeconds(
                (long) jwtProperties.getRefreshExpirationDays() * 24 * 60 * 60));
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessExpirationSeconds(),
                UserResponse.from(user)
        );
    }

    private void ensureAccountNotLocked(User user) {
        if (user.getStatus() != UserStatus.LOCKED) {
            return;
        }

        if (user.getLockedUntil() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is locked.");
        }

        if (Instant.now().isBefore(user.getLockedUntil())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Account is locked. Try again later.");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= authProperties.getMaxFailedAttempts()) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(Instant.now().plusSeconds(
                    (long) authProperties.getLockDurationMinutes() * 60));
        }

        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
    }
}
