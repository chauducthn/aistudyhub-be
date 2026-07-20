package com.studyhub.aistudyhubbe.service.rag;

import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentExtractionStatus;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.service.DocumentStorageService;
import com.studyhub.aistudyhubbe.service.DocumentStorageService.DownloadedDocumentFile;
import com.studyhub.aistudyhubbe.service.DocumentTextExtractionService;
import com.studyhub.aistudyhubbe.service.DocumentTextExtractionService.ExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentIndexEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexEventListener.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentTextExtractionService documentTextExtractionService;
    private final DocumentChunkIndexer documentChunkIndexer;

    public DocumentIndexEventListener(
            DocumentRepository documentRepository,
            DocumentStorageService documentStorageService,
            DocumentTextExtractionService documentTextExtractionService,
            DocumentChunkIndexer documentChunkIndexer) {
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.documentTextExtractionService = documentTextExtractionService;
        this.documentChunkIndexer = documentChunkIndexer;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentIndexRequestedEvent event) {
        Document document = documentRepository.findById(event.documentId()).orElse(null);
        if (document == null) {
            LOGGER.debug("Skipping background processing because document {} no longer exists", event.documentId());
            return;
        }

        Path tempPath = null;
        try {
            String storageKey = document.getS3Key() == null || document.getS3Key().isBlank()
                    ? document.getFileUrl()
                    : document.getS3Key();
            DownloadedDocumentFile downloadedFile = documentStorageService.downloadDocumentFile(storageKey);

            tempPath = Files.createTempFile(
                    "aistudyhub-doc-async-",
                    "." + document.getFileType().toLowerCase(Locale.ROOT));
            Files.write(tempPath, downloadedFile.bytes());

            ExtractionResult extraction = documentTextExtractionService.extract(tempPath, document.getFileType());
            document.setExtractionStatus(extraction.status());
            document.setExtractedText(extraction.text());
            document.setExtractionError(extraction.error());
            document.setExtractedAt(extraction.extractedAt());
        } catch (Exception ex) {
            document.setExtractionStatus(DocumentExtractionStatus.FAILED);
            document.setExtractionError("Async text extraction failed: " + ex.getMessage());
            LOGGER.error("Could not extract document {} after upload", event.documentId(), ex);
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ex) {
                    LOGGER.warn("Could not delete temporary extraction file for document {}", event.documentId(), ex);
                }
            }
        }

        documentRepository.save(document);
        documentChunkIndexer.indexDocument(event.documentId());
    }
}
