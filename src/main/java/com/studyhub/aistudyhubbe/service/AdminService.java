package com.studyhub.aistudyhubbe.service;

import com.studyhub.aistudyhubbe.dto.AdminDashboardMetricsResponse;
import com.studyhub.aistudyhubbe.dto.AdminUserResponse;
import com.studyhub.aistudyhubbe.dto.PageResponse;
import com.studyhub.aistudyhubbe.entity.User;
import com.studyhub.aistudyhubbe.entity.UserStatus;
import com.studyhub.aistudyhubbe.exception.ApiException;
import com.studyhub.aistudyhubbe.repository.RefreshTokenRepository;
import com.studyhub.aistudyhubbe.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final double BYTES_PER_GB = 1024D * 1024D * 1024D;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StorageUsageService storageUsageService;

    public AdminService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            StorageUsageService storageUsageService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.storageUsageService = storageUsageService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users;
        if (search == null || search.isBlank()) {
            users = userRepository.findAll(pageable);
        } else {
            String keyword = search.trim();
            users = userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    keyword,
                    keyword,
                    pageable);
        }

        return PageResponse.from(users.map(AdminUserResponse::from));
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long actorId, Long userId, UserStatus status) {
        if (actorId.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admins cannot change their own account status");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        user.setStatus(status);
        if (status == UserStatus.ACTIVE) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        } else {
            user.setLockedUntil(null);
            refreshTokenRepository.revokeAllByUserId(user.getId());
        }

        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AdminDashboardMetricsResponse getDashboardMetrics() {
        Instant sevenDaysAgo = Instant.now().minusSeconds(7L * 24 * 60 * 60);
        long usedBytes = storageUsageService.calculateUsedBytes();
        double usedGb = round2(usedBytes / BYTES_PER_GB);
        double percentUsed = round2((usedGb / StorageUsageService.STORAGE_LIMIT_GB) * 100D);

        return new AdminDashboardMetricsResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.LOCKED),
                userRepository.countByCreatedAtAfter(sevenDaysAgo),
                0L,
                new AdminDashboardMetricsResponse.StorageMetrics(
                        usedBytes,
                        usedGb,
                        StorageUsageService.STORAGE_LIMIT_GB,
                        percentUsed,
                        usedGb > StorageUsageService.STORAGE_LIMIT_GB
                ),
                buildUserGrowth()
        );
    }

    private List<AdminDashboardMetricsResponse.DailyUserMetric> buildUserGrowth() {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate startDate = LocalDate.now(zoneId).minusDays(6);
        Instant startInstant = startDate.atStartOfDay(zoneId).toInstant();
        List<User> recentUsers = userRepository.findByCreatedAtAfter(startInstant);

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
                .sorted(Comparator.comparing(AdminDashboardMetricsResponse.DailyUserMetric::date))
                .toList();
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
