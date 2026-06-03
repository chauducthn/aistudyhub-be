package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.exception.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentStorageService {

    public static final long MAX_DOCUMENT_SIZE_BYTES = 20L * 1024L * 1024L;

    private static final String PUBLIC_PATH_PREFIX = "/uploads/documents/";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf",
            "doc",
            "docx",
            "ppt",
            "pptx",
            "txt",
            "rtf",
            "md",
            "xls",
            "xlsx",
            "csv",
            "odt",
            "ods",
            "odp"
    );
    private static final Map<String, String> EXPECTED_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("md", "text/markdown"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("csv", "text/csv"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("odp", "application/vnd.oasis.opendocument.presentation")
    );

    private final Path documentStoragePath;

    public DocumentStorageService(@Value("${app.storage.document-dir}") String documentStorageDir) {
        this.documentStoragePath = Paths.get(documentStorageDir).toAbsolutePath().normalize();
    }

    public StoredDocumentFile storeDocument(Long userId, MultipartFile file) {
        validateFile(file);

        String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        String userFolder = "user-" + userId;
        String storedFilename = UUID.randomUUID() + "." + extension;

        try {
            Path userStoragePath = documentStoragePath.resolve(userFolder).normalize();
            Files.createDirectories(userStoragePath);

            Path destination = userStoragePath.resolve(storedFilename).normalize();
            if (!destination.startsWith(userStoragePath)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document file name");
            }

            file.transferTo(destination);
            return new StoredDocumentFile(
                    PUBLIC_PATH_PREFIX + userFolder + "/" + storedFilename,
                    originalFilename,
                    extension.toUpperCase(Locale.ROOT),
                    file.getSize()
            );
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store document file");
        }
    }

    public Path getDocumentStoragePath() {
        return documentStoragePath;
    }

    public Path resolveDocumentPath(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(PUBLIC_PATH_PREFIX)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document file not found");
        }

        String relativePath = fileUrl.substring(PUBLIC_PATH_PREFIX.length());
        Path documentPath = documentStoragePath.resolve(relativePath).normalize();
        if (!documentPath.startsWith(documentStoragePath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document file path");
        }

        if (!Files.exists(documentPath) || !Files.isRegularFile(documentPath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document file not found");
        }

        return documentPath;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document file is required");
        }

        if (file.getSize() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document file must not exceed 20MB");
        }

        String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Document must be a PDF, Word, PowerPoint, TXT, Markdown, Excel, CSV, or OpenDocument file");
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            return;
        }

        String expectedContentType = EXPECTED_CONTENT_TYPES.get(extension);
        if (expectedContentType != null && !expectedContentType.equalsIgnoreCase(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document content type does not match the file extension");
        }
    }

    private String cleanOriginalFilename(String originalFilename) {
        String cleanName = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        cleanName = StringUtils.getFilename(cleanName);
        if (cleanName.isBlank() || cleanName.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document file name");
        }
        return cleanName;
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document file extension is required");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredDocumentFile(
            String fileUrl,
            String originalFilename,
            String fileType,
            long fileSize
    ) {
    }
}
