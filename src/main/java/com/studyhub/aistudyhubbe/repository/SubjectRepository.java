package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.Subject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Subject> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}
