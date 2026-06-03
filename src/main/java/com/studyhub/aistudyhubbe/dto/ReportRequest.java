package com.studyhub.aistudyhubbe.dto;

import com.studyhub.aistudyhubbe.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull(message = "Report reason is required")
        ReportReason reason,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description
) {
}
