package com.studyhub.aistudyhubbe.repository;

import com.studyhub.aistudyhubbe.entity.Report;
import com.studyhub.aistudyhubbe.entity.ReportStatus;
import com.studyhub.aistudyhubbe.repository.projection.ReportStatusCount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @Query(
            value = """
                    select r from Report r
                    join fetch r.document d
                    join fetch r.reporter reporter
                    where (:status is null or r.status = :status)
                    """,
            countQuery = """
                    select count(r) from Report r
                    where (:status is null or r.status = :status)
                    """)
    Page<Report> searchReports(@Param("status") ReportStatus status, Pageable pageable);

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

    long countByStatus(ReportStatus status);

    @Query("""
            select r.status as status, count(r) as total
            from Report r
            group by r.status
            """)
    List<ReportStatusCount> countGroupedByStatus();
}
