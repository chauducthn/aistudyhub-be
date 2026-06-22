package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.AdminDashboardMetricsResponse;
import com.studyhub.aistudyhubbe.entity.DocumentStatus;
import com.studyhub.aistudyhubbe.entity.ReportStatus;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import com.studyhub.aistudyhubbe.repository.ChatMessageRepository;
import com.studyhub.aistudyhubbe.repository.DocumentRepository;
import com.studyhub.aistudyhubbe.repository.ReportRepository;
import com.studyhub.aistudyhubbe.repository.SubjectRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminMetricsService {

    private static final double BYTES_PER_GB = 1024D * 1024D * 1024D;
    private static final long CACHE_TTL_MS = 15_000L;

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final SubjectRepository subjectRepository;
    private final ReportRepository reportRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StorageUsageService storageUsageService;
    private final Object metricsCacheLock = new Object();
    private volatile AdminDashboardMetricsResponse cachedMetrics;
    private volatile long cachedMetricsAtMs;

    public AdminMetricsService(
            UserRepository userRepository,
            DocumentRepository documentRepository,
            SubjectRepository subjectRepository,
            ReportRepository reportRepository,
            ChatMessageRepository chatMessageRepository,
            StorageUsageService storageUsageService) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.subjectRepository = subjectRepository;
        this.reportRepository = reportRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.storageUsageService = storageUsageService;
    }

    public AdminDashboardMetricsResponse getDashboardMetrics() {
        long now = System.currentTimeMillis();
        AdminDashboardMetricsResponse snapshot = cachedMetrics;
        if (snapshot != null && now - cachedMetricsAtMs < CACHE_TTL_MS) {
            return snapshot;
        }

        synchronized (metricsCacheLock) {
            now = System.currentTimeMillis();
            snapshot = cachedMetrics;
            if (snapshot != null && now - cachedMetricsAtMs < CACHE_TTL_MS) {
                return snapshot;
            }

            AdminDashboardMetricsResponse freshMetrics = loadDashboardMetrics();
            cachedMetrics = freshMetrics;
            cachedMetricsAtMs = System.currentTimeMillis();
            return freshMetrics;
        }
    }

    private AdminDashboardMetricsResponse loadDashboardMetrics() {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate startDate = LocalDate.now(zoneId).minusDays(6);
        Instant startInstant = startDate.atStartOfDay(zoneId).toInstant();
        Instant sevenDaysAgo = Instant.now().minusSeconds(7L * 24 * 60 * 60);

        EnumMap<UserStatus, Long> userCounts =
                mapCounts(UserStatus.class, userRepository.countGroupedByStatus());
        EnumMap<DocumentStatus, Long> documentCounts =
                mapCounts(DocumentStatus.class, documentRepository.countGroupedByStatus());
        EnumMap<ReportStatus, Long> reportCounts =
                mapCounts(ReportStatus.class, reportRepository.countGroupedByStatus());
        List<User> recentUsers = userRepository.findByCreatedAtAfter(startInstant);

        long usedBytes = storageUsageService.calculateUsedBytes();
        double usedGb = round2(usedBytes / BYTES_PER_GB);
        double percentUsed = round2((usedGb / StorageUsageService.STORAGE_LIMIT_GB) * 100D);

        return new AdminDashboardMetricsResponse(
                total(userCounts),
                count(userCounts, UserStatus.ACTIVE),
                count(userCounts, UserStatus.LOCKED),
                countRecentUsers(recentUsers, sevenDaysAgo),
                chatMessageRepository.count(),
                buildDocumentMetrics(documentCounts),
                new AdminDashboardMetricsResponse.SubjectMetrics(subjectRepository.count()),
                buildReportMetrics(reportCounts),
                new AdminDashboardMetricsResponse.StorageMetrics(
                        usedBytes,
                        usedGb,
                        StorageUsageService.STORAGE_LIMIT_GB,
                        percentUsed,
                        usedGb > StorageUsageService.STORAGE_LIMIT_GB
                ),
                buildUserGrowth(recentUsers, startDate, zoneId)
        );
    }

    private AdminDashboardMetricsResponse.DocumentMetrics buildDocumentMetrics(
            EnumMap<DocumentStatus, Long> counts) {
        long hiddenDocuments = count(counts, DocumentStatus.HIDDEN);
        long lockedDocuments = count(counts, DocumentStatus.LOCKED);
        long removedDocuments = count(counts, DocumentStatus.REMOVED);

        return new AdminDashboardMetricsResponse.DocumentMetrics(
                total(counts),
                count(counts, DocumentStatus.PUBLIC),
                count(counts, DocumentStatus.PRIVATE),
                hiddenDocuments,
                lockedDocuments,
                removedDocuments,
                count(counts, DocumentStatus.DELETED),
                hiddenDocuments + lockedDocuments + removedDocuments
        );
    }

    private AdminDashboardMetricsResponse.ReportMetrics buildReportMetrics(
            EnumMap<ReportStatus, Long> counts) {
        return new AdminDashboardMetricsResponse.ReportMetrics(
                total(counts),
                count(counts, ReportStatus.PENDING),
                count(counts, ReportStatus.REVIEWED),
                count(counts, ReportStatus.REJECTED),
                count(counts, ReportStatus.RESOLVED)
        );
    }

    private List<AdminDashboardMetricsResponse.DailyUserMetric> buildUserGrowth(
            List<User> recentUsers,
            LocalDate startDate,
            ZoneId zoneId) {
        return java.util.stream.IntStream.rangeClosed(0, 6)
                .mapToObj(startDate::plusDays)
                .map(date -> new AdminDashboardMetricsResponse.DailyUserMetric(
                        date,
                        recentUsers.stream()
                                .map(User::getCreatedAt)
                                .filter(createdAt -> createdAt != null)
                                .map(createdAt -> createdAt.atZone(zoneId).toLocalDate())
                                .filter(date::equals)
                                .count()))
                .toList();
    }

    private <E extends Enum<E>> EnumMap<E, Long> mapCounts(Class<E> enumType, List<Object[]> rows) {
        EnumMap<E, Long> counts = new EnumMap<>(enumType);
        for (Object[] row : rows) {
            if (row.length >= 2 && enumType.isInstance(row[0]) && row[1] instanceof Number total) {
                counts.put(enumType.cast(row[0]), total.longValue());
            }
        }
        return counts;
    }

    private <E extends Enum<E>> long count(EnumMap<E, Long> counts, E key) {
        return counts.getOrDefault(key, 0L);
    }

    private <E extends Enum<E>> long total(EnumMap<E, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private long countRecentUsers(List<User> recentUsers, Instant sevenDaysAgo) {
        return recentUsers.stream()
                .map(User::getCreatedAt)
                .filter(createdAt -> createdAt != null && createdAt.isAfter(sevenDaysAgo))
                .count();
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
