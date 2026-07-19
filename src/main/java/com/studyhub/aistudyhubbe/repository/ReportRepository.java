package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.Report;
import com.studyhub.aistudyhubbe.entity.ReportStatus;
import com.studyhub.aistudyhubbe.repository.projection.ReportStatusCount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Modifying
    @Query("delete from Report r where r.reporter.id = :userId or r.document.user.id = :userId")
    void deleteByUserInvolvement(@Param("userId") Long userId);

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @Query(
            value = """
                    select r from Report r
                    join fetch r.document d
                    join fetch r.reporter reporter
                    where (:status is null or r.status = :status)
                      and (
                          :keyword is null or :keyword = ''
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(reporter.email) like lower(concat('%', :keyword, '%'))
                          or lower(reporter.fullName) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(r.description, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(r) from Report r
                    join r.document d
                    join r.reporter reporter
                    where (:status is null or r.status = :status)
                      and (
                          :keyword is null or :keyword = ''
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(reporter.email) like lower(concat('%', :keyword, '%'))
                          or lower(reporter.fullName) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(r.description, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """)
    Page<Report> searchReports(
            @Param("status") ReportStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(
            value = """
                    select r from Report r
                    join fetch r.document d
                    join fetch r.reporter reporter
                    where r.status in (com.studyhub.aistudyhubbe.entity.ReportStatus.RESOLVED, com.studyhub.aistudyhubbe.entity.ReportStatus.REJECTED)
                      and (
                          :keyword is null or :keyword = ''
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(reporter.email) like lower(concat('%', :keyword, '%'))
                          or lower(reporter.fullName) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(r.description, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    select count(r) from Report r
                    join r.document d
                    join r.reporter reporter
                    where r.status in (com.studyhub.aistudyhubbe.entity.ReportStatus.RESOLVED, com.studyhub.aistudyhubbe.entity.ReportStatus.REJECTED)
                      and (
                          :keyword is null or :keyword = ''
                          or lower(d.title) like lower(concat('%', :keyword, '%'))
                          or lower(reporter.email) like lower(concat('%', :keyword, '%'))
                          or lower(reporter.fullName) like lower(concat('%', :keyword, '%'))
                          or lower(coalesce(r.description, '')) like lower(concat('%', :keyword, '%'))
                      )
                    """)
    Page<Report> findProcessedReports(
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            select r from Report r
            join fetch r.document d
            join fetch r.reporter reporter
            where r.id = :id
            """)
    Optional<Report> findWithDocumentAndReporterById(@Param("id") Long id);

    Optional<Report> findByDocumentIdAndReporterIdAndStatus(
            Long documentId,
            Long reporterId,
            ReportStatus status);

    boolean existsByDocumentIdAndReporterIdAndStatus(
            Long documentId,
            Long reporterId,
            ReportStatus status);

    boolean existsByDocumentId(Long documentId);

    @Modifying
    @Query("delete from Report r where r.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    long countByStatus(ReportStatus status);

    @Query("""
            select r.status as status, count(r) as total
            from Report r
            group by r.status
            """)
    List<ReportStatusCount> countGroupedByStatus();
}
