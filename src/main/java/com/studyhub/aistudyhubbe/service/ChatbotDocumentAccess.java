package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatbotDocumentAccess {

    private static final List<DocumentStatus> EXCLUDED_STATUSES = List.of(
            DocumentStatus.DELETED,
            DocumentStatus.REMOVED,
            DocumentStatus.LOCKED
    );

    private final DocumentRepository documentRepository;

    public ChatbotDocumentAccess(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Document findAccessibleDocument(Long userId, Long documentId) {
        if (documentId == null) {
            return null;
        }

        Document document = documentRepository.findAdminDetailById(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        if (EXCLUDED_STATUSES.contains(document.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document not found");
        }

        Long ownerId = document.getUser().getId();
        if (ownerId.equals(userId) || document.getStatus() == DocumentStatus.PUBLIC) {
            return document;
        }

        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to chat with this private document.");
    }
}
