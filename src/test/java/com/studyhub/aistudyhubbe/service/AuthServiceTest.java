package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.config.AuthProperties;
import com.studyhub.aistudyhubbe.config.JwtProperties;
import com.studyhub.aistudyhubbe.dto.AuthResponse;
import com.studyhub.aistudyhubbe.dto.LoginRequest;
import com.studyhub.aistudyhubbe.dto.RegisterRequest;
import com.studyhub.aistudyhubbe.entity.Role;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.PasswordResetTokenRepository;
import com.studyhub.aistudyhubbe.repository.RefreshTokenRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;
    @Mock private AuthProperties authProperties;

    @InjectMocks
    private AuthService authService;

    private User activeUser;
    private User lockedUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setEmail("test@studyhub.local");
        activeUser.setPasswordHash("hashed_password");
        activeUser.setFullName("Test User");
        activeUser.setRole(Role.USER);
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.setFailedLoginAttempts(0);

        lockedUser = new User();
        lockedUser.setId(2L);
        lockedUser.setEmail("locked@studyhub.local");
        lockedUser.setPasswordHash("hashed_password");
        lockedUser.setStatus(UserStatus.LOCKED);
        lockedUser.setLockedUntil(Instant.now().plusSeconds(3600)); // locked for 1 hour
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("new@studyhub.local", "Password123", "New User");
        
        when(userRepository.existsByEmailIgnoreCase("new@studyhub.local")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed_new_password");
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("mocked_jwt_token");
        when(jwtService.getAccessExpirationSeconds()).thenReturn(3600L);
        
        // Mock save to return the user (though our code doesn't strictly use the returned user, it's good practice)
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mocked_jwt_token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_EmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("test@studyhub.local", "Password123", "Test User");
        when(userRepository.existsByEmailIgnoreCase("test@studyhub.local")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> authService.register(request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Email already registered", exception.getMessage());
        
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("test@studyhub.local", "password");
        
        when(userRepository.findByEmailIgnoreCase("test@studyhub.local")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password", "hashed_password")).thenReturn(true);
        when(jwtService.generateAccessToken(activeUser)).thenReturn("mocked_jwt_token");
        
        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked_jwt_token", response.accessToken());
        verify(refreshTokenRepository).revokeAllByUserId(activeUser.getId());
        verify(userRepository).save(activeUser); // reset failed attempts
    }

    @Test
    void login_InvalidPassword() {
        LoginRequest request = new LoginRequest("test@studyhub.local", "wrong_password");
        
        when(userRepository.findByEmailIgnoreCase("test@studyhub.local")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);
        when(authProperties.getMaxFailedAttempts()).thenReturn(5);

        ApiException exception = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        
        verify(userRepository).save(activeUser); // should increment failed attempts
        assertEquals(1, activeUser.getFailedLoginAttempts());
    }

    @Test
    void login_AccountLocked() {
        LoginRequest request = new LoginRequest("locked@studyhub.local", "password");
        
        when(userRepository.findByEmailIgnoreCase("locked@studyhub.local")).thenReturn(Optional.of(lockedUser));

        ApiException exception = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertTrue(exception.getMessage().contains("Account is locked"));
    }
}
