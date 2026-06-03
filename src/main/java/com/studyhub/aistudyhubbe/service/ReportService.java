package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.dto.ReportRequest;
import com.studyhub.aistudyhubbe.dto.ReportResponse;
import com.studyhub.aistudyhubbe.dto.ResolveReportRequest;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.Report;
import com.studyhub.aistudyhubbe.entity.ReportStatus;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.ReportRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public ReportService(
            ReportRepository reportRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReportResponse createReport(Long reporterId, Long documentId, ReportRequest request) {
        User reporter = findUser(reporterId);
        Document document = documentRepository.findByIdAndStatus(documentId, DocumentStatus.PUBLIC)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getUser().getId().equals(reporterId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot report your own document");
        }

        boolean hasPendingReport = reportRepository.existsByDocumentIdAndReporterIdAndStatus(
                documentId,
                reporterId,
                ReportStatus.PENDING);
        if (hasPendingReport) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You already have a pending report for this document");
        }

        Report report = new Report();
        report.setDocument(document);
        report.setReporter(reporter);
        report.setReason(request.reason());
        report.setDescription(normalizeOptionalText(request.description()));
        report.setStatus(ReportStatus.PENDING);

        return ReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> listReports(ReportStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ReportResponse> reports = reportRepository.searchReports(status, pageable)
                .map(ReportResponse::from);
        return PageResponse.from(reports);
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Long reportId) {
        Report report = reportRepository.findWithDocumentAndReporterById(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
        return ReportResponse.from(report);
    }

    @Transactional
    public ReportResponse resolveReport(Long reportId, ResolveReportRequest request) {
        Report report = reportRepository.findWithDocumentAndReporterById(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));

        if (report.getStatus() != ReportStatus.PENDING && report.getStatus() != ReportStatus.REVIEWED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Report has already been resolved");
        }

        ReportStatus requestedStatus = request.status();
        if (requestedStatus == ReportStatus.REJECTED) {
            report.setStatus(ReportStatus.REJECTED);
        } else if (requestedStatus == ReportStatus.RESOLVED) {
            DocumentStatus documentStatus = request.documentStatus();
            if (!isModerationDocumentStatus(documentStatus)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Resolved report requires documentStatus HIDDEN, LOCKED, or REMOVED");
            }
            report.getDocument().setStatus(documentStatus);
            report.setStatus(ReportStatus.RESOLVED);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Report status must be REJECTED or RESOLVED");
        }

        report.setAdminNote(normalizeOptionalText(request.adminNote()));
        report.setResolvedAt(Instant.now());
        return ReportResponse.from(reportRepository.save(report));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private boolean isModerationDocumentStatus(DocumentStatus status) {
        return status == DocumentStatus.HIDDEN
                || status == DocumentStatus.LOCKED
                || status == DocumentStatus.REMOVED;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }
}
