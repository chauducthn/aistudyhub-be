package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(
            value = """
                    select date(created_at) as day, count(*) as total
                    from users
                    where created_at >= :startInstant
                    group by date(created_at)
                    """,
            nativeQuery = true)
    List<Object[]> countDailyRegistrations(@Param("startInstant") Instant startInstant);
}
