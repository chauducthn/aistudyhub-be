package com.studyhub.aistudyhubbe.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminDashboardMetricsResponse(
        long totalUsers,
        long activeUsers,
        long lockedUsers,
        long newUsersLast7Days,
        long chatbotApiCalls,
        StorageMetrics storage,
        List<DailyUserMetric> userGrowth
) {

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
