package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByUserIdAndVisibleToUserTrue(Long userId, Pageable pageable);

    Page<ChatMessage> findByUserIdAndChatSessionIdAndVisibleToUserTrue(Long userId, Long sessionId, Pageable pageable);

    @Modifying
    @Query("update ChatMessage c set c.visibleToUser = false where c.user.id = :userId")
    void hideByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update ChatMessage c set c.visibleToUser = false where c.chatSession.id = :sessionId and c.user.id = :userId")
    void hideBySessionId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    @Modifying
    @Query("delete from ChatMessage c where c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
