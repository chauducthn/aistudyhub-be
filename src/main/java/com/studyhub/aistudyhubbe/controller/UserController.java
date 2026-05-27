package com.studyhub.aistudyhubbe.controller;

import com.studyhub.aistudyhubbe.dto.ApiResponse;
import com.studyhub.aistudyhubbe.dto.ChangePasswordRequest;
import com.studyhub.aistudyhubbe.dto.UpdateProfileRequest;
import com.studyhub.aistudyhubbe.dto.UserResponse;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.security.UserPrincipal;
import com.studyhub.aistudyhubbe.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Current user profile")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get current authenticated user")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        return ApiResponse.ok(userService.getCurrentUser(principal.getId()));
    }

    @Operation(summary = "Update current user profile")
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = requireUserId(principal);
        return ApiResponse.ok("Profile updated", userService.updateProfile(userId, request));
    }

    @Operation(summary = "Upload current user avatar")
    @PatchMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponse> updateAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("avatar") MultipartFile avatar) {
        Long userId = requireUserId(principal);
        return ApiResponse.ok("Avatar updated", userService.updateAvatar(userId, avatar));
    }

    @Operation(summary = "Delete current user avatar")
    @DeleteMapping("/me/avatar")
    public ApiResponse<UserResponse> deleteAvatar(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = requireUserId(principal);
        return ApiResponse.ok("Avatar deleted", userService.deleteAvatar(userId));
    }

    @Operation(summary = "Change current user password")
    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = requireUserId(principal);
        userService.changePassword(userId, request);
        return ApiResponse.ok("Password changed", null);
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }
}
