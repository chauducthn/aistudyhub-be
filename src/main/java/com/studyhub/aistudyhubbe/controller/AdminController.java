package com.studyhub.aistudyhubbe.controller;

import com.studyhub.aistudyhubbe.dto.AdminDashboardMetricsResponse;
import com.studyhub.aistudyhubbe.dto.AdminDocumentResponse;
import com.studyhub.aistudyhubbe.dto.AdminDocumentStatusRequest;
import com.studyhub.aistudyhubbe.dto.AdminUserResponse;
import com.studyhub.aistudyhubbe.dto.ApiResponse;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.dto.ReportResponse;
import com.studyhub.aistudyhubbe.dto.ResolveReportRequest;
import com.studyhub.aistudyhubbe.dto.UpdateUserStatusRequest;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.ReportStatus;
import com.studyhub.aistudyhubbe.security.UserPrincipal;
import com.studyhub.aistudyhubbe.service.AdminService;
import com.studyhub.aistudyhubbe.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ReportService reportService;

    public AdminController(AdminService adminService, ReportService reportService) {
        this.adminService = adminService;
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, String>> summary() {
        return ApiResponse.ok(Map.of("status", "ADMIN_OK"));
    }

    @Operation(summary = "List users for admin management")
    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminService.listUsers(search, page, size));
    }

    @Operation(summary = "Update a user account status")
    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.ok(
                "User status updated",
                adminService.updateUserStatus(principal.getId(), userId, request.status()));
    }

    @Operation(summary = "Get admin dashboard metrics")
    @GetMapping("/dashboard/metrics")
    public ApiResponse<AdminDashboardMetricsResponse> dashboardMetrics() {
        return ApiResponse.ok(adminService.getDashboardMetrics());
    }

    @Operation(summary = "List all documents for admin moderation")
    @GetMapping("/documents")
    public ApiResponse<PageResponse<AdminDocumentResponse>> listDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminService.listDocuments(keyword, status, userId, page, size));
    }

    @Operation(summary = "Get a document detail for admin moderation")
    @GetMapping("/documents/{documentId}")
    public ApiResponse<AdminDocumentResponse> getDocument(@PathVariable Long documentId) {
        return ApiResponse.ok(adminService.getDocument(documentId));
    }

    @Operation(summary = "Update a document status for admin moderation")
    @PatchMapping("/documents/{documentId}/status")
    public ApiResponse<AdminDocumentResponse> updateDocumentStatus(
            @PathVariable Long documentId,
            @Valid @RequestBody AdminDocumentStatusRequest request) {
        return ApiResponse.ok(
                "Document status updated",
                adminService.updateDocumentStatus(documentId, request.status()));
    }

    @Operation(summary = "List document reports for admin review")
    @GetMapping("/reports")
    public ApiResponse<PageResponse<ReportResponse>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(reportService.listReports(status, page, size));
    }

    @Operation(summary = "Get a document report detail for admin review")
    @GetMapping("/reports/{reportId}")
    public ApiResponse<ReportResponse> getReport(@PathVariable Long reportId) {
        return ApiResponse.ok(reportService.getReport(reportId));
    }

    @Operation(summary = "Resolve or reject a document report")
    @PatchMapping("/reports/{reportId}/resolve")
    public ApiResponse<ReportResponse> resolveReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ResolveReportRequest request) {
        return ApiResponse.ok(
                "Report reviewed",
                reportService.resolveReport(reportId, request));
    }
}
