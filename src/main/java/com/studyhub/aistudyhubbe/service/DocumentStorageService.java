package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@Profile("!test")
public class DocumentStorageService {

    public static final long MAX_DOCUMENT_SIZE_BYTES = 20L * 1024L * 1024L;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "txt", "rtf", "md", "xls", "xlsx", "csv", "odt", "ods", "odp"
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

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;
    private final String region;

    public DocumentStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucketName,
            @Value("${aws.region}") String region) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
        this.region = region;
    }

    public StoredDocumentFile storeDocument(Long userId, MultipartFile file) {
        validateFile(file);

        String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        String contentType = resolveContentType(file, extension);
        String s3Key = buildS3Key(userId, extension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .metadata(Map.of("original-filename", originalFilename))
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read document file for S3 upload");
        } catch (S3Exception | SdkClientException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store document file on S3");
        }

        return new StoredDocumentFile(
                s3Key,
                buildS3Url(s3Key),
                originalFilename,
                extension.toUpperCase(Locale.ROOT),
                file.getSize(),
                contentType
        );
    }

    public Path downloadToTempFile(String s3KeyOrUrl) {
        String s3Key = resolveS3Key(s3KeyOrUrl);

        try {
            Path tempFile = Files.createTempFile("aistudyhub-doc-", ".tmp");
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.getObject(getObjectRequest, ResponseTransformer.toFile(tempFile));
            return tempFile;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create temporary document file");
        } catch (S3Exception | SdkClientException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to download document from S3");
        }
    }

    public String createDownloadUrl(String s3KeyOrUrl, String originalFilename) {
        String s3Key = resolveS3Key(s3KeyOrUrl);
        String contentDisposition = "inline; filename=\"" + safeDownloadFilename(originalFilename) + "\"";

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .responseContentDisposition(contentDisposition)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
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
        if (cleanName == null || cleanName.isBlank() || cleanName.contains("..")) {
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

    private String resolveContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            return EXPECTED_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
        }
        return contentType;
    }

    private String buildS3Key(Long userId, String extension) {
        return "documents/"
                + userId + "/"
                + LocalDate.now() + "/"
                + UUID.randomUUID()
                + "."
                + extension;
    }

    private String buildS3Url(String s3Key) {
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + s3Key;
    }

    private String resolveS3Key(String s3KeyOrUrl) {
        if (s3KeyOrUrl == null || s3KeyOrUrl.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Document S3 key is missing");
        }

        if (!s3KeyOrUrl.startsWith("http://") && !s3KeyOrUrl.startsWith("https://")) {
            return s3KeyOrUrl;
        }

        try {
            String path = URI.create(s3KeyOrUrl).getPath();
            String key = path.startsWith("/") ? path.substring(1) : path;
            return URLDecoder.decode(key, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid document S3 URL");
        }
    }

    private String safeDownloadFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }
        return filename.replace("\"", "");
    }

    public record StoredDocumentFile(
            String s3Key,
            String fileUrl,
            String originalFilename,
            String fileType,
            long fileSize,
            String contentType
    ) {
        public StoredDocumentFile(String fileUrl, String originalFilename, String fileType, long fileSize) {
            this(null, fileUrl, originalFilename, fileType, fileSize, null);
        }
    }
}
