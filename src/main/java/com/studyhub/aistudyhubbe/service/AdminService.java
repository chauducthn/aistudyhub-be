package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.AdminDocumentResponse;
import com.studyhub.aistudyhubbe.dto.AdminUpdateUserRequest;
import com.studyhub.aistudyhubbe.dto.AdminUserResponse;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.config.CacheNames;
import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.Role;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.DocumentChunkRepository;
import com.studyhub.aistudyhubbe.repository.PasswordResetTokenRepository;
import com.studyhub.aistudyhubbe.repository.RefreshTokenRepository;
import com.studyhub.aistudyhubbe.repository.ReportRepository;
import com.studyhub.aistudyhubbe.repository.ChatMessageRepository;
import com.studyhub.aistudyhubbe.repository.ChatSessionRepository;
import com.studyhub.aistudyhubbe.repository.SubjectRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DocumentRepository documentRepository;
    private final SubjectRepository subjectRepository;
    private final ReportRepository reportRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final DocumentStorageService documentStorageService;
    private final AvatarStorageService avatarStorageService;

    public AdminService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            DocumentRepository documentRepository,
            SubjectRepository subjectRepository,
            ReportRepository reportRepository,
            ChatMessageRepository chatMessageRepository,
            ChatSessionRepository chatSessionRepository,
            DocumentChunkRepository documentChunkRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            DocumentStorageService documentStorageService,
            AvatarStorageService avatarStorageService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.documentRepository = documentRepository;
        this.subjectRepository = subjectRepository;
        this.reportRepository = reportRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.documentStorageService = documentStorageService;
        this.avatarStorageService = avatarStorageService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String search, Role role, int page, int size) {
        Page<User> users = userRepository.searchUsers(
                search == null ? "" : search.trim(),
                role,
                adminPageable(page, size)
        );

        return PageResponse.from(users.map(u -> AdminUserResponse.from(u, avatarStorageService.presignAvatarUrl(u.getAvatarUrl()))));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDocumentResponse> listDocuments(
            String keyword,
            DocumentStatus status,
            Long userId,
            int page,
            int size) {
        Page<AdminDocumentResponse> documents = documentRepository.searchAdminDocuments(
                        normalizeKeyword(keyword),
                        status,
                        userId,
                        adminPageable(page, size))
                .map(AdminDocumentResponse::from);
        return PageResponse.from(documents);
    }

    @Transactional(readOnly = true)
    public AdminDocumentResponse getDocument(Long documentId) {
        Document document = documentRepository.findAdminDetailById(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        return AdminDocumentResponse.from(document);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true),
            @CacheEvict(value = CacheNames.PUBLIC_DOCUMENTS, allEntries = true)
    })
    public AdminDocumentResponse updateDocumentStatus(Long documentId, DocumentStatus status) {
        if (!isAdminDocumentStatus(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Admin document status must be PUBLIC, PRIVATE, HIDDEN, LOCKED, or REMOVED");
        }

        Document document = documentRepository.findAdminDetailById(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        document.setStatus(status);
        return AdminDocumentResponse.from(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public String getDocumentDownloadUrl(Long documentId) {
        Document document = documentRepository.findAdminDetailById(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getS3Key() == null || document.getS3Key().isBlank()) {
            return document.getFileUrl();
        }

        return documentStorageService.createDownloadUrl(document.getS3Key(), document.getOriginalFilename());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true),
            @CacheEvict(value = CacheNames.PUBLIC_DOCUMENTS, allEntries = true)
    })
    public void deleteDocument(Long documentId) {
        Document document = documentRepository.findAdminDetailById(documentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        chatMessageRepository.nullifyDocumentId(document.getId());
        reportRepository.deleteByDocumentId(document.getId());
        documentChunkRepository.deleteByDocumentId(document.getId());
        documentRepository.delete(document);
    }

    @Transactional
    @CacheEvict(value = CacheNames.ADMIN_DASHBOARD, allEntries = true)
    public AdminUserResponse updateUserStatus(Long actorId, Long userId, UserStatus status) {
        rejectSelfAction(actorId, userId, "Admins cannot change their own account status");
        User user = getUserOrThrow(userId);

        user.setStatus(status);
        if (status == UserStatus.ACTIVE) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        } else {
            user.setLockedUntil(null);
            refreshTokenRepository.revokeAllByUserId(user.getId());
        }

        User savedUser = userRepository.save(user);
        return AdminUserResponse.from(savedUser, avatarStorageService.presignAvatarUrl(savedUser.getAvatarUrl()));
    }

    @Transactional
    public AdminUserResponse updateUser(Long userId, AdminUpdateUserRequest request) {
        User user = getUserOrThrow(userId);

        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());

        User savedUser = userRepository.save(user);
        return AdminUserResponse.from(savedUser, avatarStorageService.presignAvatarUrl(savedUser.getAvatarUrl()));
    }

    @Transactional
    public AdminUserResponse resetPassword(Long userId, String newPassword) {
        User user = getUserOrThrow(userId);

        if (newPassword == null || newPassword.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password is required");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetRequired(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        passwordResetTokenRepository.invalidateAllByUserId(user.getId());

        User savedUser = userRepository.save(user);
        return AdminUserResponse.from(savedUser, avatarStorageService.presignAvatarUrl(savedUser.getAvatarUrl()));
    }

    @Transactional
    public void deleteUser(Long actorId, Long userId) {
        rejectSelfAction(actorId, userId, "Admins cannot delete their own account");
        User user = getUserOrThrow(userId);

        reportRepository.deleteByUserInvolvement(user.getId());
        chatMessageRepository.nullifyDocumentIdByUserId(user.getId());
        chatMessageRepository.deleteByUserId(user.getId());
        chatSessionRepository.deleteByUserId(user.getId());
        documentChunkRepository.deleteByUserId(user.getId());
        documentRepository.deleteByUserId(user.getId());
        subjectRepository.deleteByUserId(user.getId());
        refreshTokenRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
    }

    private boolean isAdminDocumentStatus(DocumentStatus status) {
        return status == DocumentStatus.PUBLIC
                || status == DocumentStatus.PRIVATE
                || status == DocumentStatus.HIDDEN
                || status == DocumentStatus.LOCKED
                || status == DocumentStatus.REMOVED;
    }

    private Pageable adminPageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void rejectSelfAction(Long actorId, Long userId, String message) {
        if (actorId.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
