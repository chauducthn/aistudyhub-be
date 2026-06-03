package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.Report;
import com.studyhub.aistudyhubbe.entity.ReportReason;
import com.studyhub.aistudyhubbe.entity.ReportStatus;
import java.time.Instant;

public record ReportResponse(
        Long id,
        Long documentId,
        String documentTitle,
        String documentStatus,
        Long reporterId,
        String reporterEmail,
        ReportReason reason,
        String description,
        ReportStatus status,
        String adminNote,
        Instant createdAt,
        Instant resolvedAt
) {

    public static ReportResponse from(Report report) {
        Document document = report.getDocument();
        return new ReportResponse(
                report.getId(),
                document.getId(),
                document.getTitle(),
                document.getStatus().name(),
                report.getReporter().getId(),
                report.getReporter().getEmail(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getAdminNote(),
                report.getCreatedAt(),
                report.getResolvedAt()
        );
    }
}
