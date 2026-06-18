package com.studyhub.aistudyhubbe.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.studyhub.aistudyhubbe.exception.ApiException;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarStorageService {

    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );
    private static final Set<String> ALLOWED_CONTENT_TYPES = EXTENSIONS_BY_CONTENT_TYPE.keySet();

    private final Cloudinary cloudinary;

    public AvatarStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String storeAvatar(Long userId, MultipartFile file, String currentAvatarUrl) {
        validateFile(file);

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", "aistudyhub/avatars"
            ));
            
            deleteAvatar(currentAvatarUrl);
            return uploadResult.get("secure_url").toString();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store avatar on Cloudinary");
        }
    }

    public void deleteAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }

        try {
            if (avatarUrl.contains("cloudinary.com")) {
                String[] parts = avatarUrl.split("/");
                String filenameWithExt = parts[parts.length - 1];
                String publicId = "aistudyhub/avatars/" + filenameWithExt.substring(0, filenameWithExt.lastIndexOf('.'));
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            }
        } catch (Exception ignored) {
            // A stale avatar file should not block profile updates.
        }
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
