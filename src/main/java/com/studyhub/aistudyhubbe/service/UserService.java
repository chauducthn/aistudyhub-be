package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.ChangePasswordRequest;
import com.studyhub.aistudyhubbe.dto.UpdateProfileRequest;
import com.studyhub.aistudyhubbe.dto.UserResponse;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorageService avatarStorageService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AvatarStorageService avatarStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.avatarStorageService = avatarStorageService;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setFullName(request.fullName().trim());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile avatar) {
        User user = findUser(userId);
        String avatarUrl = avatarStorageService.storeAvatar(user.getId(), avatar, user.getAvatarUrl());
        user.setAvatarUrl(avatarUrl);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse deleteAvatar(Long userId) {
        User user = findUser(userId);
        avatarStorageService.deleteAvatar(user.getAvatarUrl());
        user.setAvatarUrl(null);
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(User user) {
        UserResponse response = UserResponse.from(user);
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            return response;
        }
        return new UserResponse(
                response.id(),
                response.email(),
                response.fullName(),
                avatarStorageService.presignAvatarUrl(user.getAvatarUrl()),
                response.role(),
                response.status());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

}
