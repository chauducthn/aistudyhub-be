package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import java.time.Instant;

public record DocumentResponse(
        Long id,
        Long userId,
        Long subjectId,
        String subjectName,
        String title,
        String description,
        String fileType,
        long fileSize,
        String fileUrl,
        String originalFilename,
        DocumentStatus status,
        DocumentExtractionStatus extractionStatus,
        String extractionError,
        Instant extractedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getUser().getId(),
                document.getSubject() == null ? null : document.getSubject().getId(),
                document.getSubject() == null ? null : document.getSubject().getName(),
                document.getTitle(),
                document.getDescription(),
                document.getFileType(),
                document.getFileSize(),
                document.getFileUrl(),
                document.getOriginalFilename(),
                document.getStatus(),
                document.getExtractionStatus(),
                document.getExtractionError(),
                document.getExtractedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
