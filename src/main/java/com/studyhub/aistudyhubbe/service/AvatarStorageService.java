package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.exception.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarStorageService {

    private static final String PUBLIC_PATH_PREFIX = "/uploads/avatars/";
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );
    private static final Set<String> ALLOWED_CONTENT_TYPES = EXTENSIONS_BY_CONTENT_TYPE.keySet();

    private final Path avatarStoragePath;

    public AvatarStorageService(@Value("${app.storage.avatar-dir}") String avatarStorageDir) {
        this.avatarStoragePath = Paths.get(avatarStorageDir).toAbsolutePath().normalize();
    }

    public String storeAvatar(Long userId, MultipartFile file, String currentAvatarUrl) {
        validateFile(file);

        try {
            Files.createDirectories(avatarStoragePath);
            String extension = EXTENSIONS_BY_CONTENT_TYPE.get(file.getContentType());
            String fileName = "user-" + userId + "-" + UUID.randomUUID() + extension;
            Path destination = avatarStoragePath.resolve(fileName).normalize();

            if (!destination.startsWith(avatarStoragePath)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid avatar file name");
            }

            file.transferTo(destination);
            deleteAvatar(currentAvatarUrl);
            return PUBLIC_PATH_PREFIX + fileName;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store avatar file");
        }
    }

    public void deleteAvatar(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(PUBLIC_PATH_PREFIX)) {
            return;
        }

        String fileName = avatarUrl.substring(PUBLIC_PATH_PREFIX.length());
        Path avatarPath = avatarStoragePath.resolve(fileName).normalize();
        if (!avatarPath.startsWith(avatarStoragePath)) {
            return;
        }

        try {
            Files.deleteIfExists(avatarPath);
        } catch (IOException ignored) {
            // A stale avatar file should not block profile updates.
        }
    }

    public Path getAvatarStoragePath() {
        return avatarStoragePath;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Avatar file is required");
        }

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Avatar must be a JPG, PNG, WEBP, or GIF image");
        }
    }
}
