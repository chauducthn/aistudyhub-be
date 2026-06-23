package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenAndUsedFalse(String token);

    @Modifying
    @Query("UPDATE PasswordResetToken prt SET prt.used = true WHERE prt.user.id = :userId AND prt.used = false")
    void invalidateAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from PasswordResetToken prt where prt.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
