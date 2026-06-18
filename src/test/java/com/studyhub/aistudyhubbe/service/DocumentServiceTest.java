package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.DocumentResponse;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.Role;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.SubjectRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private DocumentStorageService documentStorageService;
    @Mock private DocumentTextExtractionService documentTextExtractionService;

    @InjectMocks
    private DocumentService documentService;

    private User testUser;
    private Document testDocument;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@studyhub.local");
        testUser.setRole(Role.USER);

        testDocument = new Document();
        testDocument.setId(10L);
        testDocument.setUser(testUser);
        testDocument.setTitle("Test Doc");
        testDocument.setFileType("PDF");
        testDocument.setFileSize(1024L);
        testDocument.setFileUrl("uploads/test.pdf");
        testDocument.setStatus(DocumentStatus.PRIVATE);
    }

    @Test
    void uploadDocument_Success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
        DocumentStorageService.StoredDocumentFile storedFile = new DocumentStorageService.StoredDocumentFile(
                "uploads/test.pdf", "test.pdf", "PDF", 13L
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        // No subject provided
        when(documentStorageService.storeDocument(eq(1L), any())).thenReturn(storedFile);
        when(documentTextExtractionService.extract(any(), eq("PDF")))
                .thenReturn(DocumentTextExtractionService.ExtractionResult.extracted("dummy content"));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(10L); // mock auto-generated ID
            return doc;
        });

        DocumentResponse response = documentService.uploadDocument(1L, "My PDF", "Description", null, file);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("My PDF", response.title());
        assertEquals("PDF", response.fileType());
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void uploadDocument_UserNotFound() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> 
            documentService.uploadDocument(99L, "Title", "Desc", null, file)
        );
        
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void getDocument_Success() {
        when(documentRepository.findVisibleByIdAndUserId(eq(10L), eq(1L), anyList()))
                .thenReturn(Optional.of(testDocument));

        DocumentResponse response = documentService.getDocument(1L, 10L);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Test Doc", response.title());
    }

    @Test
    void getDocument_NotFound() {
        when(documentRepository.findVisibleByIdAndUserId(eq(99L), eq(1L), anyList()))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> documentService.getDocument(1L, 99L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}
