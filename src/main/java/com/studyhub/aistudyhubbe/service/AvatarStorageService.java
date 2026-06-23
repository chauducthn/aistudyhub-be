package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@Profile("!test")
public class AvatarStorageService {

    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final String S3_AVATAR_PREFIX = "avatars/";
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );
    private static final Set<String> ALLOWED_CONTENT_TYPES = EXTENSIONS_BY_CONTENT_TYPE.keySet();

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public AvatarStorageService(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucketName,
            @Value("${aws.region}") String region) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
    }

    public String storeAvatar(Long userId, MultipartFile file, String currentAvatarUrl) {
        validateFile(file);

        String avatarUrl = storeS3Avatar(userId, file);
        deleteAvatar(currentAvatarUrl);
        return avatarUrl;
    }

    public void deleteAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }

        String s3Key = resolveS3AvatarKey(avatarUrl);
        if (s3Key == null) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
        } catch (S3Exception | SdkClientException ignored) {
            // A stale avatar file should not block profile updates.
        }
    }

    private String storeS3Avatar(Long userId, MultipartFile file) {
        String extension = EXTENSIONS_BY_CONTENT_TYPE.get(file.getContentType());
        String s3Key = S3_AVATAR_PREFIX
                + userId + "/"
                + LocalDate.now() + "/"
                + UUID.randomUUID()
                + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
            return buildS3Url(s3Key);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read avatar file for S3 upload");
        } catch (S3Exception | SdkClientException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store avatar on S3");
        }
    }

    private String resolveS3AvatarKey(String avatarUrl) {
        if (avatarUrl.startsWith(S3_AVATAR_PREFIX)) {
            return avatarUrl;
        }
        if (!avatarUrl.startsWith("http://") && !avatarUrl.startsWith("https://")) {
            return null;
        }

        try {
            String key = URI.create(avatarUrl).getPath();
            key = key.startsWith("/") ? key.substring(1) : key;
            key = URLDecoder.decode(key, StandardCharsets.UTF_8);
            if (key.startsWith(S3_AVATAR_PREFIX)) {
                return key;
            }
            return null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Avatar file is required");
        }

        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Avatar image must not exceed 5MB");
        }

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Avatar must be a JPG, PNG, WEBP, or GIF image");
        }
    }

    private String buildS3Url(String s3Key) {
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + s3Key;
    }
}
