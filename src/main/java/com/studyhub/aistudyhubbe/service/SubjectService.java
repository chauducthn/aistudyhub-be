package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.SubjectRequest;
import com.studyhub.aistudyhubbe.dto.SubjectResponse;
import com.studyhub.aistudyhubbe.entity.Subject;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.SubjectRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public SubjectService(
            SubjectRepository subjectRepository,
            UserRepository userRepository,
            DocumentRepository documentRepository) {
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public SubjectResponse createSubject(Long userId, SubjectRequest request) {
        String name = normalizeName(request.name());
        ensureUniqueName(userId, name);

        Subject subject = new Subject();
        subject.setUser(findUser(userId));
        subject.setName(name);

        return SubjectResponse.from(subjectRepository.save(subject));
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> listSubjects(Long userId) {
        return subjectRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SubjectResponse::from)
                .toList();
    }

    @Transactional
    public SubjectResponse updateSubject(Long userId, Long subjectId, SubjectRequest request) {
        Subject subject = findOwnedSubject(userId, subjectId);
        String name = normalizeName(request.name());

        if (!subject.getName().equalsIgnoreCase(name)
                && subjectRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, subjectId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Subject name already exists");
        }

        subject.setName(name);
        return SubjectResponse.from(subjectRepository.save(subject));
    }

    @Transactional
    public void deleteSubject(Long userId, Long subjectId) {
        Subject subject = findOwnedSubject(userId, subjectId);
        if (documentRepository.countBySubjectId(subject.getId()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete subject with existing documents");
        }
        subjectRepository.delete(subject);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Subject findOwnedSubject(Long userId, Long subjectId) {
        return subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subject not found"));
    }

    private void ensureUniqueName(Long userId, String name) {
        if (subjectRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Subject name already exists");
        }
    }

    private String normalizeName(String name) {
        return name.trim();
    }
}
