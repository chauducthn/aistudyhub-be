package com.studyhub.aistudyhubbe.controller;

import com.studyhub.aistudyhubbe.dto.ApiResponse;
import com.studyhub.aistudyhubbe.dto.ReportRequest;
import com.studyhub.aistudyhubbe.dto.ReportResponse;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.security.UserPrincipal;
import com.studyhub.aistudyhubbe.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/reports")
@Tag(name = "Reports", description = "Document report submission")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Report a public document")
    @PostMapping
    public ApiResponse<ReportResponse> createReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long documentId,
            @Valid @RequestBody ReportRequest request) {
        return ApiResponse.ok(
                "Report submitted",
                reportService.createReport(requireUserId(principal), documentId, request));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }
}
