package com.studyhub.aistudyhubbe.controller;

import com.studyhub.aistudyhubbe.dto.ApiResponse;
import com.studyhub.aistudyhubbe.dto.AuthResponse;
import com.studyhub.aistudyhubbe.dto.LoginRequest;
import com.studyhub.aistudyhubbe.dto.RegisterRequest;
import com.studyhub.aistudyhubbe.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Đăng ký, đăng nhập, đăng xuất")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Đăng ký tài khoản mới")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        attachRefreshCookie(response, authResponse.user().id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful", authResponse));
    }

    @Operation(summary = "Đăng nhập — nhận JWT + cookie refresh")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        attachRefreshCookie(response, authResponse.user().id());
        return ResponseEntity.ok(ApiResponse.ok("Login successful", authResponse));
    }

    @Operation(summary = "Đăng xuất — thu hồi refresh token")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = AuthService.REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        clearRefreshCookie(response);
        return ResponseEntity.ok(ApiResponse.ok("Logout successful", null));
    }

    @Operation(summary = "Lam moi access token tu HttpOnly refresh cookie")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = AuthService.REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.refresh(refreshToken);
        attachRefreshCookie(response, authResponse.user().id());
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", authResponse));
    }

    private void attachRefreshCookie(HttpServletResponse response, Long userId) {
        String refreshToken = authService.createRefreshToken(userId);
        response.addHeader("Set-Cookie", buildRefreshCookie(refreshToken, false).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildRefreshCookie("", true).toString());
    }

    private ResponseCookie buildRefreshCookie(String value, boolean clear) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(AuthService.REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax");

        if (clear) {
            builder.maxAge(0);
        } else {
            builder.maxAge(7L * 24 * 60 * 60);
        }

        return builder.build();
    }
}
