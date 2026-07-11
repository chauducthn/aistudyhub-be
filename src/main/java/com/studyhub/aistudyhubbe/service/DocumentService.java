package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.DocumentUpdateRequest;
import com.studyhub.aistudyhubbe.dto.DocumentSubjectRequest;
import com.studyhub.aistudyhubbe.dto.DocumentVisibilityRequest;
import com.studyhub.aistudyhubbe.dto.DocumentResponse;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.config.CacheNames;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.Subject;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import java.time.Instant;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.SubjectRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import com.studyhub.aistudyhubbe.service.DocumentStorageService.StoredDocumentFile;
import com.studyhub.aistudyhubbe.service.DocumentStorageService.DownloadedDocumentFile;
import com.studyhub.aistudyhubbe.service.DocumentTextExtractionService.ExtractionResult;
import com.studyhub.aistudyhubbe.service.rag.DocumentChunkIndexer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    private final DocumentChunkIndexer documentChunkIndexer;
    private final ChatbotAiResponder chatbotAiResponder;

    public DocumentService(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            SubjectRepository subjectRepository,
            DocumentStorageService documentStorageService,
            DocumentTextExtractionService documentTextExtractionService,
            DocumentChunkIndexer documentChunkIndexer,
            ChatbotAiResponder chatbotAiResponder) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.documentStorageService = documentStorageService;
        this.documentTextExtractionService = documentTextExtractionService;
        this.documentChunkIndexer = documentChunkIndexer;
        this.chatbotAiResponder = chatbotAiResponder;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PUBLIC_DOCUMENTS, allEntries = true),
            @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true)
    })
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

        Document saved = documentRepository.save(document);
        documentChunkIndexer.indexDocument(saved);
        return DocumentResponse.from(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PUBLIC_DOCUMENTS, allEntries = true),
            @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true)
    })
    public List<DocumentResponse> uploadDocuments(
            Long userId,
            String title,
            String description,
            Long subjectId,
            List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one file is required");
        }

        User user = findUser(userId);
        Subject subject = findOwnedSubject(userId, subjectId);
        List<DocumentResponse> responses = new java.util.ArrayList<>();

        for (MultipartFile file : files) {
            String fileTitle = title;
            if (fileTitle == null || fileTitle.trim().isBlank()) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename != null) {
                    int lastDot = originalFilename.lastIndexOf('.');
                    fileTitle = lastDot > 0 ? originalFilename.substring(0, lastDot) : originalFilename;
                } else {
                    fileTitle = "Untitled Document";
                }
            }

            StoredDocumentFile storedFile = documentStorageService.storeDocument(userId, file);

            Document document = new Document();
            document.setUser(user);
            document.setSubject(subject);
            document.setTitle(normalizeTitle(fileTitle));
            document.setDescription(normalizeDescription(description));
            document.setFileType(storedFile.fileType());
            document.setContentType(storedFile.contentType());
            document.setFileSize(storedFile.fileSize());
            document.setS3Key(storedFile.s3Key());
            document.setFileUrl(storedFile.fileUrl());
            document.setOriginalFilename(storedFile.originalFilename());
            document.setStatus(DocumentStatus.PRIVATE);
            applyExtractionResult(document, storedFile, file);

            Document saved = documentRepository.save(document);
            documentChunkIndexer.indexDocument(saved);
            responses.add(DocumentResponse.from(saved));
        }

        return responses;
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
        ).map(document -> DocumentResponse.fromOwnedDocument(document, userId));

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
    @Cacheable(
            value = CacheNames.PUBLIC_DOCUMENTS,
            key = "T(String).valueOf(#keyword == null ? '' : #keyword)"
                    + " + '|' + #pageable.pageNumber + '|' + #pageable.pageSize + '|' + #pageable.sort")
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
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PUBLIC_DOCUMENTS, allEntries = true),
            @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true)
    })
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
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PUBLIC_DOCUMENTS, allEntries = true),
            @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true)
    })
    public DocumentResponse updateDocumentSubject(Long userId, Long documentId, DocumentSubjectRequest request) {
        Document document = findOwnedVisibleDocument(userId, documentId);
        document.setSubject(findOwnedSubject(userId, request.subjectId()));
        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PUBLIC_DOCUMENTS, allEntries = true),
            @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true)
    })
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
    public DocumentDownloadFile getDocumentDownloadFile(Long userId, Long documentId) {
        Document document = documentRepository.findDownloadableById(
                        documentId,
                        userId,
                        DocumentStatus.PUBLIC,
                        EXCLUDED_NORMAL_STATUSES)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        String storageKey = document.getS3Key() == null || document.getS3Key().isBlank()
                ? document.getFileUrl()
                : document.getS3Key();
        DownloadedDocumentFile file = documentStorageService.downloadDocumentFile(storageKey);
        String contentType = file.contentType() == null || file.contentType().isBlank()
                ? document.getContentType()
                : file.contentType();

        return new DocumentDownloadFile(
                file.bytes(),
                document.getOriginalFilename(),
                contentType,
                file.contentLength());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PUBLIC_DOCUMENTS, allEntries = true),
            @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true)
    })
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

    @Transactional
    public DocumentResponse checkPlagiarism(Long userId, Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        if (!document.getUser().getId().equals(userId) && document.getStatus() != DocumentStatus.PUBLIC) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to access this document");
        }

        String text = document.getExtractedText();
        if (text == null || text.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document text is not extracted. Cannot perform plagiarism check.");
        }

        String sampleText = text.substring(0, Math.min(text.length(), 6000));
        String prompt = """
                Analyze the following document for potential plagiarism by comparing it with your training data and public knowledge on the Internet.
                Identify:
                1. Plagiarism Score (0-100%).
                2. Potential source links or publications names that match closely.
                3. Specific sentences or paragraphs that appear to be copied or paraphrased from external sources.
                4. An overall analysis of the document's originality and integrity.

                Output the report as structured, clean Markdown. Do not repeat this instruction.

                Document Content:
                %s
                """.formatted(sampleText);

        ChatbotAiResponder.StudyAiResponse reportResponse = chatbotAiResponder.generate(prompt, null);
        document.setPlagiarismReport(reportResponse.response());
        document.setPlagiarismCheckedAt(Instant.now());

        return DocumentResponse.from(documentRepository.save(document));
    }

    public record DocumentDownloadFile(
            byte[] bytes,
            String originalFilename,
            String contentType,
            long contentLength
    ) {}
}
