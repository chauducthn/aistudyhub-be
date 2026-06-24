package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.Subject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Subject> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    @Modifying
    @Query("delete from Subject s where s.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
