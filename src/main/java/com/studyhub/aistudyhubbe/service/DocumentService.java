package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.DocumentUpdateRequest;
import com.studyhub.aistudyhubbe.dto.DocumentSubjectRequest;
import com.studyhub.aistudyhubbe.dto.DocumentVisibilityRequest;
import com.studyhub.aistudyhubbe.dto.DocumentResponse;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.Subject;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.SubjectRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import com.studyhub.aistudyhubbe.service.DocumentStorageService.StoredDocumentFile;
import com.studyhub.aistudyhubbe.service.DocumentTextExtractionService.ExtractionResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public DocumentService(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            DocumentStorageService documentStorageService,
            DocumentTextExtractionService documentTextExtractionService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.documentStorageService = documentStorageService;
        this.documentTextExtractionService = documentTextExtractionService;
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
        document.setContentType(storedFile.contentType());
        document.setFileSize(storedFile.fileSize());
        document.setS3Key(storedFile.s3Key());
        document.setFileUrl(storedFile.fileUrl());
        document.setOriginalFilename(storedFile.originalFilename());
        document.setStatus(DocumentStatus.PRIVATE);
        applyExtractionResult(document, storedFile, file);

        return DocumentResponse.from(documentRepository.save(document));
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
    public String getDocumentDownloadUrl(Long userId, Long documentId) {
        Document document = documentRepository.findDownloadableById(
                        documentId,
                        userId,
                        DocumentStatus.PUBLIC,
                        EXCLUDED_NORMAL_STATUSES)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getS3Key() == null || document.getS3Key().isBlank()) {
            return document.getFileUrl();
        }

        return documentStorageService.createDownloadUrl(document.getS3Key(), document.getOriginalFilename());
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

    private void applyExtractionResult(Document document, StoredDocumentFile storedFile, MultipartFile file) {
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile(
                    "aistudyhub-doc-upload-",
                    "." + storedFile.fileType().toLowerCase(Locale.ROOT));
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }
            ExtractionResult extraction = documentTextExtractionService.extract(tempPath, storedFile.fileType());
            document.setExtractionStatus(extraction.status());
            document.setExtractedText(extraction.text());
            document.setExtractionError(extraction.error());
            document.setExtractedAt(extraction.extractedAt());
        } catch (IOException ex) {
            document.setExtractionStatus(DocumentExtractionStatus.FAILED);
            document.setExtractionError("Could not prepare document for text extraction");
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        }
    }

}
