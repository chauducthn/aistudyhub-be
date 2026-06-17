package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import java.time.Instant;

public record AdminDocumentResponse(
        Long id,
        Long userId,
        String userEmail,
        String userFullName,
        Long subjectId,
        String subjectName,
        String title,
        String description,
        String fileType,
        String contentType,
        long fileSize,
        String s3Key,
        String fileUrl,
        String originalFilename,
        DocumentStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminDocumentResponse from(Document document) {
        return new AdminDocumentResponse(
                document.getId(),
                document.getUser().getId(),
                document.getUser().getEmail(),
                document.getUser().getFullName(),
                document.getSubject() == null ? null : document.getSubject().getId(),
                document.getSubject() == null ? null : document.getSubject().getName(),
                document.getTitle(),
                document.getDescription(),
                document.getFileType(),
                document.getContentType(),
                document.getFileSize(),
                document.getS3Key(),
                document.getFileUrl(),
                document.getOriginalFilename(),
                document.getStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
