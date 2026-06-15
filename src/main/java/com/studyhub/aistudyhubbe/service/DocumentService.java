package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.DocumentUpdateRequest;
import com.studyhub.aistudyhubbe.dto.DocumentSubjectRequest;
import com.studyhub.aistudyhubbe.dto.DocumentVisibilityRequest;
import com.studyhub.aistudyhubbe.dto.DocumentResponse;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.Subject;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.SubjectRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import com.studyhub.aistudyhubbe.service.DocumentStorageService.StoredDocumentFile;
import com.studyhub.aistudyhubbe.service.DocumentTextExtractionService.ExtractionResult;
import com.studyhub.aistudyhubbe.service.rag.DocumentChunkIndexer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final List<DocumentStatus> EXCLUDED_NORMAL_STATUSES = List.of(
            DocumentStatus.DELETED,
            DocumentStatus.REMOVED,
            DocumentStatus.LOCKED
    );

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentTextExtractionService documentTextExtractionService;
    private final DocumentChunkIndexer documentChunkIndexer;

    public DocumentService(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            DocumentStorageService documentStorageService,
            DocumentTextExtractionService documentTextExtractionService,
            DocumentChunkIndexer documentChunkIndexer) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.documentStorageService = documentStorageService;
        this.documentTextExtractionService = documentTextExtractionService;
        this.documentChunkIndexer = documentChunkIndexer;
    }

    @Transactional
    public DocumentResponse uploadDocument(
            Long userId,
            String title,
            String description,
            Long subjectId,
            MultipartFile file) {
        User user = findUser(userId);
        Subject subject = findOwnedSubject(userId, subjectId);
        StoredDocumentFile storedFile = documentStorageService.storeDocument(userId, file);

        Document document = new Document();
        document.setUser(user);
        document.setSubject(subject);
        document.setTitle(normalizeTitle(title));
        document.setDescription(normalizeDescription(description));
        document.setFileType(storedFile.fileType());
        document.setFileSize(storedFile.fileSize());
        document.setFileUrl(storedFile.fileUrl());
        document.setOriginalFilename(storedFile.originalFilename());
        document.setStatus(DocumentStatus.PRIVATE);
        applyExtractionResult(document, storedFile);

        Document saved = documentRepository.save(document);
        documentChunkIndexer.indexDocument(saved);
        return DocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> listDocuments(
            Long userId,
            String keyword,
            Long subjectId,
            DocumentStatus status,
            Pageable pageable) {
        Page<DocumentResponse> documents = documentRepository.searchUserDocuments(
                userId,
                normalizeKeyword(keyword),
                subjectId,
                status,
                EXCLUDED_NORMAL_STATUSES,
                pageable
        ).map(DocumentResponse::from);

        return PageResponse.from(documents);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long userId, Long documentId) {
        Document document = documentRepository.findVisibleByIdAndUserId(
                        documentId,
                        userId,
                        EXCLUDED_NORMAL_STATUSES)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> listPublicDocuments(String keyword, Pageable pageable) {
        Page<DocumentResponse> documents = documentRepository.searchPublicDocuments(
                normalizeKeyword(keyword),
                DocumentStatus.PUBLIC,
                pageable
        ).map(DocumentResponse::from);

        return PageResponse.from(documents);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getPublicDocument(Long documentId) {
        Document document = documentRepository.findByIdAndStatusWithSubject(documentId, DocumentStatus.PUBLIC)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        return DocumentResponse.from(document);
    }

    @Transactional
    public DocumentResponse updateDocument(Long userId, Long documentId, DocumentUpdateRequest request) {
        Document document = findOwnedVisibleDocument(userId, documentId);

        if (request.title() != null) {
            document.setTitle(normalizeTitle(request.title()));
        }

        if (request.description() != null) {
            document.setDescription(normalizeDescription(request.description()));
        }

        if (request.subjectId() != null) {
            document.setSubject(findOwnedSubject(userId, request.subjectId()));
        }

        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional
    public DocumentResponse updateDocumentSubject(Long userId, Long documentId, DocumentSubjectRequest request) {
        Document document = findOwnedVisibleDocument(userId, documentId);
        document.setSubject(findOwnedSubject(userId, request.subjectId()));
        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional
    public DocumentResponse updateVisibility(Long userId, Long documentId, DocumentVisibilityRequest request) {
        Document document = findOwnedVisibleDocument(userId, documentId);
        DocumentStatus status = request.status();

        if (status != DocumentStatus.PUBLIC && status != DocumentStatus.PRIVATE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Visibility status must be PUBLIC or PRIVATE");
        }

        document.setStatus(status);
        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public DownloadedDocument downloadDocument(Long userId, Long documentId) {
        Document document = documentRepository.findDownloadableById(
                        documentId,
                        userId,
                        DocumentStatus.PUBLIC,
                        EXCLUDED_NORMAL_STATUSES)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        Path documentPath = documentStorageService.resolveDocumentPath(document.getFileUrl());
        try {
            Resource resource = new UrlResource(documentPath.toUri());
            return new DownloadedDocument(
                    resource,
                    document.getOriginalFilename(),
                    resolveContentType(documentPath, document.getFileType()),
                    Files.size(documentPath)
            );
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read document file");
        }
    }

    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        Document document = findOwnedVisibleDocument(userId, documentId);
        document.setStatus(DocumentStatus.DELETED);
        documentRepository.save(document);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Subject findOwnedSubject(Long userId, Long subjectId) {
        if (subjectId == null) {
            return null;
        }

        return subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subject not found"));
    }

    private Document findOwnedVisibleDocument(Long userId, Long documentId) {
        return documentRepository.findVisibleByIdAndUserId(
                        documentId,
                        userId,
                        EXCLUDED_NORMAL_STATUSES)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private String normalizeTitle(String title) {
        if (title == null || title.trim().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document title is required");
        }

        String trimmedTitle = title.trim();
        if (trimmedTitle.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document title must not exceed 255 characters");
        }
        return trimmedTitle;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.trim().isBlank()) {
            return null;
        }

        String trimmedDescription = description.trim();
        if (trimmedDescription.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document description must not exceed 2000 characters");
        }
        return trimmedDescription;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private void applyExtractionResult(Document document, StoredDocumentFile storedFile) {
        Path documentPath = documentStorageService.resolveDocumentPath(storedFile.fileUrl());
        ExtractionResult extraction = documentTextExtractionService.extract(documentPath, storedFile.fileType());
        document.setExtractionStatus(extraction.status());
        document.setExtractedText(extraction.text());
        document.setExtractionError(extraction.error());
        document.setExtractedAt(extraction.extractedAt());
    }

    private String resolveContentType(Path documentPath, String fileType) {
        try {
            String probedContentType = Files.probeContentType(documentPath);
            if (probedContentType != null && !probedContentType.isBlank()) {
                return probedContentType;
            }
        } catch (IOException ignored) {
            // Fall back to stored file type below.
        }

        return switch (fileType) {
            case "PDF" -> "application/pdf";
            case "DOC" -> "application/msword";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "PPT" -> "application/vnd.ms-powerpoint";
            case "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "TXT" -> "text/plain";
            case "RTF" -> "application/rtf";
            case "MD" -> "text/markdown";
            case "XLS" -> "application/vnd.ms-excel";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "CSV" -> "text/csv";
            case "ODT" -> "application/vnd.oasis.opendocument.text";
            case "ODS" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "ODP" -> "application/vnd.oasis.opendocument.presentation";
            default -> "application/octet-stream";
        };
    }

    public record DownloadedDocument(
            Resource resource,
            String filename,
            String contentType,
            long contentLength
    ) {
    }
}
