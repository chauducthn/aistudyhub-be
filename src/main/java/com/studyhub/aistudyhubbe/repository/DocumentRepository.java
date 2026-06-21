package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.Document;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserId(Long userId);

    @Modifying
    @Query("delete from Document d where d.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    Page<Document> findByUserIdAndStatusNotIn(
            Long userId,
            Collection<DocumentStatus> excludedStatuses,
            Pageable pageable);

    Page<Document> findByUserIdAndSubjectIdAndStatusNotIn(
            Long userId,
            Long subjectId,
            Collection<DocumentStatus> excludedStatuses,
            Pageable pageable);

    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);

    Optional<Document> findByIdAndUserId(Long id, Long userId);

    Optional<Document> findByIdAndStatus(Long id, DocumentStatus status);

    @Query("""
            select d from Document d
            join fetch d.user
            left join fetch d.subject
            where d.id = :documentId
            """)
    Optional<Document> findAdminDetailById(@Param("documentId") Long documentId);

    @Query("""
            select d from Document d
            left join fetch d.subject
            where d.id = :documentId
              and d.status = :status
            """)
    Optional<Document> findByIdAndStatusWithSubject(
            @Param("documentId") Long documentId,
            @Param("status") DocumentStatus status);

    @Query("""
            select d from Document d
            left join fetch d.subject
            where d.id = :documentId
              and d.user.id = :userId
              and d.status not in :excludedStatuses
            """)
    Optional<Document> findVisibleByIdAndUserId(
            @Param("documentId") Long documentId,
            @Param("userId") Long userId,
            @Param("excludedStatuses") Collection<DocumentStatus> excludedStatuses);

    @Query("""
            select d from Document d
            left join fetch d.subject
            where d.id = :documentId
              and d.status not in :excludedStatuses
              and (d.user.id = :userId or d.status = :publicStatus)
            """)
    Optional<Document> findDownloadableById(
            @Param("documentId") Long documentId,
            @Param("userId") Long userId,
            @Param("publicStatus") DocumentStatus publicStatus,
            @Param("excludedStatuses") Collection<DocumentStatus> excludedStatuses);

    @Query(
            value = """
                    select d from Document d
                    left join d.subject s
                    where d.user.id = :userId
                      and d.status not in :excludedStatuses
                      and (:subjectId is null or s.id = :subjectId)
                      and (:status is null or d.status = :status)
                      and (
                          :keyword is null
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(d.originalFilename) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(d) from Document d
                    left join d.subject s
                    where d.user.id = :userId
                      and d.status not in :excludedStatuses
                      and (:subjectId is null or s.id = :subjectId)
                      and (:status is null or d.status = :status)
                      and (
                          :keyword is null
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(d.originalFilename) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """)
    Page<Document> searchUserDocuments(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("subjectId") Long subjectId,
            @Param("status") DocumentStatus status,
            @Param("excludedStatuses") Collection<DocumentStatus> excludedStatuses,
            Pageable pageable);

    @Query(
            value = """
                    select d from Document d
                    left join d.subject s
                    where d.status = :status
                      and (:keyword is null
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(d.originalFilename) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(d) from Document d
                    where d.status = :status
                      and (:keyword is null
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(d.originalFilename) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """)
    Page<Document> searchPublicDocuments(
            @Param("keyword") String keyword,
            @Param("status") DocumentStatus status,
            Pageable pageable);

    @Query(
            value = """
                    select d from Document d
                    join d.user u
                    left join d.subject s
                    where (:status is null or d.status = :status)
                      and (:userId is null or u.id = :userId)
                      and (
                          :keyword is null
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(d.originalFilename) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%'))
                          or lower(u.email) like lower(concat('%', :keyword, '%'))
                          or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(s.name, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(d) from Document d
                    join d.user u
                    left join d.subject s
                    where (:status is null or d.status = :status)
                      and (:userId is null or u.id = :userId)
                      and (
                          :keyword is null
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(d.originalFilename) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%'))
                          or lower(u.email) like lower(concat('%', :keyword, '%'))
                          or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(s.name, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """)
    Page<Document> searchAdminDocuments(
            @Param("keyword") String keyword,
            @Param("status") DocumentStatus status,
            @Param("userId") Long userId,
            Pageable pageable);

    long countByStatus(DocumentStatus status);

    long countBySubjectId(Long subjectId);
}
