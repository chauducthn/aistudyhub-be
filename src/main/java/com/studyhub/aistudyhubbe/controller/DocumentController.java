package com.studyhub.aistudyhubbe.controller;

import com.studyhub.aistudyhubbe.dto.ApiResponse;
import com.studyhub.aistudyhubbe.dto.DocumentResponse;
import com.studyhub.aistudyhubbe.dto.DocumentSubjectRequest;
import com.studyhub.aistudyhubbe.dto.DocumentUpdateRequest;
import com.studyhub.aistudyhubbe.dto.DocumentVisibilityRequest;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.security.UserPrincipal;
import com.studyhub.aistudyhubbe.service.DocumentService;
import com.studyhub.aistudyhubbe.service.DocumentService.DocumentDownloadFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Document upload and personal document management")
public class DocumentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Upload a document for the current user")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "subjectId", required = false) Long subjectId) {
        return ApiResponse.ok(
                "Document uploaded",
                documentService.uploadDocument(requireUserId(principal), title, description, subjectId, file));
    }

    @Operation(summary = "Upload multiple documents for the current user")
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<java.util.List<DocumentResponse>> uploadDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "subjectId", required = false) Long subjectId) {
        return ApiResponse.ok(
                "Documents uploaded",
                documentService.uploadDocuments(requireUserId(principal), title, description, subjectId, java.util.Arrays.asList(files)));
    }

    @Operation(summary = "List current user's documents")
    @GetMapping
    public ApiResponse<PageResponse<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return ApiResponse.ok(documentService.listDocuments(
                requireUserId(principal),
                keyword,
                subjectId,
                status,
                pageable));
    }

    @Operation(summary = "List public documents from all users")
    @GetMapping("/public")
    public ApiResponse<PageResponse<DocumentResponse>> listPublicDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireUserId(principal);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return ApiResponse.ok(documentService.listPublicDocuments(keyword, pageable));
    }

    @Operation(summary = "Get public document detail")
    @GetMapping("/public/{id}")
    public ApiResponse<DocumentResponse> getPublicDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        requireUserId(principal);
        return ApiResponse.ok(documentService.getPublicDocument(id));
    }

    @Operation(summary = "Get current user's document detail")
    @GetMapping("/{id}")
    public ApiResponse<DocumentResponse> getDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(documentService.getDocument(requireUserId(principal), id));
    }

    @Operation(summary = "Update current user's document metadata")
    @PatchMapping("/{id}")
    public ApiResponse<DocumentResponse> updateDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody DocumentUpdateRequest request) {
        return ApiResponse.ok(
                "Document updated",
                documentService.updateDocument(requireUserId(principal), id, request));
    }

    @Operation(summary = "Move a document to another subject or back to uncategorized storage")
    @PatchMapping("/{id}/subject")
    public ApiResponse<DocumentResponse> updateDocumentSubject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody DocumentSubjectRequest request) {
        return ApiResponse.ok(
                "Document subject updated",
                documentService.updateDocumentSubject(requireUserId(principal), id, request));
    }

    @Operation(summary = "Change current user's document visibility")
    @PatchMapping("/{id}/visibility")
    public ApiResponse<DocumentResponse> updateVisibility(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody DocumentVisibilityRequest request) {
        return ApiResponse.ok(
                "Document visibility updated",
                documentService.updateVisibility(requireUserId(principal), id, request));
    }

    @Operation(summary = "Download a public document or the current user's private document")
    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        DocumentDownloadFile file = documentService.getDocumentDownloadFile(requireUserId(principal), id);
        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(safeFilename(file.originalFilename()), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(resolveMediaType(file.contentType()))
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(new ByteArrayResource(file.bytes()));
    }

    @Operation(summary = "Perform plagiarism check on a document using AI comparing with Internet data")
    @PostMapping("/{id}/plagiarism-check")
    public ApiResponse<DocumentResponse> checkPlagiarism(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(
                "Plagiarism check completed",
                documentService.checkPlagiarism(requireUserId(principal), id));
    }

    @Operation(summary = "Soft delete current user's document")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        documentService.deleteDocument(requireUserId(principal), id);
        return ApiResponse.ok("Document deleted", null);
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }

    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String safeFilename(String filename) {
        return filename == null || filename.isBlank() ? "document" : filename.replace("\"", "");
    }
}
