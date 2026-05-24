package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName,
            String email,
            Pageable pageable);

    long countByStatus(UserStatus status);

    long countByCreatedAtAfter(Instant createdAt);

    List<User> findByCreatedAtAfter(Instant createdAt);
}
