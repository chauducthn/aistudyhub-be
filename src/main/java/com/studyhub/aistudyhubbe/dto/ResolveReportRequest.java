package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveReportRequest(
        @NotNull(message = "Report status is required")
        ReportStatus status,

        DocumentStatus documentStatus,

        @Size(max = 2000, message = "Admin note must not exceed 2000 characters")
        String adminNote
) {
}
