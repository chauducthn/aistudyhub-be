package com.studyhub.aistudyhubbe.config;

import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.service.AvatarStorageService;
import com.studyhub.aistudyhubbe.service.DocumentStorageService;
import com.studyhub.aistudyhubbe.service.DocumentStorageService.DownloadedDocumentFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Configuration
@Profile("test")
public class TestStorageConfig {

    @Bean(name = "testDocumentStorageService")
    @Primary
    DocumentStorageService testDocumentStorageService(
            @Value("${app.storage.document-dir:target/test-uploads/documents}") String documentDir) {
        return new LocalTestDocumentStorageService(Path.of(documentDir));
    }

    @Bean(name = "testAvatarStorageService")
    @Primary
    AvatarStorageService testAvatarStorageService(
            @Value("${app.storage.avatar-dir:target/test-uploads/avatars}") String avatarDir) {
        return new LocalTestAvatarStorageService(Path.of(avatarDir));
    }

    private static final class LocalTestDocumentStorageService extends DocumentStorageService {
        private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
                "pdf", "doc", "docx", "ppt", "pptx", "txt", "rtf", "md", "xls", "xlsx", "csv", "odt", "ods", "odp"
        );

        private final Path documentRoot;

        private LocalTestDocumentStorageService(Path documentRoot) {
            super(null, null, "test-bucket", "ap-southeast-2");
            this.documentRoot = documentRoot;
        }

        @Override
        public StoredDocumentFile storeDocument(Long userId, MultipartFile file) {
            validateFile(file);
            String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
            String extension = getExtension(originalFilename);
            String storedFilename = UUID.randomUUID() + "-" + originalFilename;
            String storageKey = "user-" + userId + "/" + storedFilename;
            Path userDir = documentRoot.resolve("user-" + userId).normalize();
            Path target = userDir.resolve(storedFilename).normalize();

            try {
                Files.createDirectories(userDir);
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store document file for test");
            }

            return new StoredDocumentFile(
                    storageKey,
                    "/uploads/documents/" + storageKey,
                    originalFilename,
                    extension.toUpperCase(Locale.ROOT),
                    file.getSize(),
                    file.getContentType()
            );
        }

        @Override
        public Path downloadToTempFile(String s3KeyOrUrl) {
            String prefix = "/uploads/documents/";
            if (s3KeyOrUrl == null || s3KeyOrUrl.isBlank()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid test document URL");
            }

            String storageKey = s3KeyOrUrl.startsWith(prefix) ? s3KeyOrUrl.substring(prefix.length()) : s3KeyOrUrl;
            Path source = documentRoot.resolve(storageKey).normalize();
            Path absoluteRoot = documentRoot.toAbsolutePath().normalize();
            Path absoluteSource = source.toAbsolutePath().normalize();
            if (!absoluteSource.startsWith(absoluteRoot)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid test document path");
            }

            try {
                Path tempFile = Files.createTempFile("aistudyhub-test-doc-", ".tmp");
                Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING);
                return tempFile;
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read test document");
            }
        }

        @Override
        public DownloadedDocumentFile downloadDocumentFile(String s3KeyOrUrl) {
            String prefix = "/uploads/documents/";
            if (s3KeyOrUrl == null || s3KeyOrUrl.isBlank()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid test document URL");
            }

            String storageKey = s3KeyOrUrl.startsWith(prefix) ? s3KeyOrUrl.substring(prefix.length()) : s3KeyOrUrl;
            Path source = documentRoot.resolve(storageKey).normalize();
            Path absoluteRoot = documentRoot.toAbsolutePath().normalize();
            Path absoluteSource = source.toAbsolutePath().normalize();
            if (!absoluteSource.startsWith(absoluteRoot)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid test document path");
            }

            try {
                byte[] bytes = Files.readAllBytes(source);
                String contentType = Files.probeContentType(source);
                return new DownloadedDocumentFile(
                        bytes,
                        contentType == null ? "application/octet-stream" : contentType,
                        bytes.length);
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read test document");
            }
        }

        @Override
        public String createDownloadUrl(String s3KeyOrUrl, String originalFilename) {
            String prefix = "/uploads/documents/";
            if (s3KeyOrUrl == null || s3KeyOrUrl.isBlank()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid test document URL");
            }
            return s3KeyOrUrl.startsWith(prefix) ? s3KeyOrUrl : prefix + s3KeyOrUrl;
        }

        private void validateFile(MultipartFile file) {
            if (file == null || file.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Document file is required");
            }
            if (file.getSize() > MAX_DOCUMENT_SIZE_BYTES) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Document file must not exceed 20MB");
            }
            String extension = getExtension(cleanOriginalFilename(file.getOriginalFilename()));
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Document must be a PDF, Word, PowerPoint, TXT, Markdown, Excel, CSV, or OpenDocument file");
            }
        }
    }

    private static final class LocalTestAvatarStorageService extends AvatarStorageService {
        private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
                "image/jpeg", "image/png", "image/webp", "image/gif"
        );

        private final Path avatarRoot;

        private LocalTestAvatarStorageService(Path avatarRoot) {
            super(null, "test-bucket", "ap-southeast-2");
            this.avatarRoot = avatarRoot;
        }

        @Override
        public String storeAvatar(Long userId, MultipartFile file, String currentAvatarUrl) {
            validateFile(file);
            String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
            String storedFilename = "user-" + userId + "-" + UUID.randomUUID() + "-" + originalFilename;
            Path target = avatarRoot.resolve(storedFilename).normalize();

            try {
                Files.createDirectories(avatarRoot);
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store avatar for test");
            }

            return "/uploads/avatars/" + storedFilename;
        }

        @Override
        public void deleteAvatar(String avatarUrl) {
            if (avatarUrl == null || !avatarUrl.startsWith("/uploads/avatars/")) {
                return;
            }
            try {
                Files.deleteIfExists(avatarRoot.resolve(avatarUrl.substring("/uploads/avatars/".length())).normalize());
            } catch (IOException ignored) {
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

    private static String cleanOriginalFilename(String originalFilename) {
        String cleanName = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        cleanName = StringUtils.getFilename(cleanName);
        if (cleanName == null || cleanName.isBlank() || cleanName.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
        return cleanName;
    }

    private static String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File extension is required");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
