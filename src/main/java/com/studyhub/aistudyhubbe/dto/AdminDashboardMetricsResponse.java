package com.studyhub.aistudyhubbe.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminDashboardMetricsResponse(
        long totalUsers,
        long activeUsers,
        long lockedUsers,
        long newUsersLast7Days,
        long chatbotApiCalls,
        DocumentMetrics documents,
        SubjectMetrics subjects,
        ReportMetrics reports,
        StorageMetrics storage,
        List<DailyUserMetric> userGrowth
) {

    public record DocumentMetrics(
            long totalDocuments,
            long publicDocuments,
            long privateDocuments,
            long hiddenDocuments,
            long lockedDocuments,
            long removedDocuments,
            long moderatedDocuments
    ) {
    }

    public record SubjectMetrics(
            long totalSubjects
    ) {
    }

    public record ReportMetrics(
            long totalReports,
            long pendingReports,
            long reviewedReports,
            long rejectedReports,
            long resolvedReports
    ) {
    }

    public record StorageMetrics(
            long usedBytes,
            double usedGb,
            double limitGb,
            double percentUsed,
            boolean overLimit
    ) {
    }

    public record DailyUserMetric(
            LocalDate date,
            long newUsers
    ) {
    }
}
