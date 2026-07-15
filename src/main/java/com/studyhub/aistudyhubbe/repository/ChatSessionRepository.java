package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.ChatSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdAndVisibleToUserTrueOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("update ChatSession c set c.visibleToUser = false where c.id = :sessionId and c.user.id = :userId")
    void hideSession(@Param("sessionId") Long sessionId, @Param("userId") Long userId);
}
