package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.DocumentChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    @Modifying
    @Query("delete from DocumentChunk c where c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    @Modifying
    @Query("delete from DocumentChunk c where c.document.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
