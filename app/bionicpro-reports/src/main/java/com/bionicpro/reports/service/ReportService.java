package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.MinioStorageException;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
import com.bionicpro.reports.model.UserReport;
import com.bionicpro.reports.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for report operations with authorization checks.
 * Ensures users can only access their own reports.
 * Integrates with MinIO for caching to reduce OLAP database load.
 */
@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final MinioReportService minioReportService;

    public ReportService(ReportRepository reportRepository, MinioReportService minioReportService) {
        this.reportRepository = reportRepository;
        this.minioReportService = minioReportService;
    }

    /**
     * Retrieves all reports for the authenticated user.
     *
     * @param currentUserId the ID of the authenticated user from JWT token
     * @return list of report responses for the user
     */
    public List<ReportResponse> getReportsForUser(Long currentUserId) {
        logger.debug("Fetching reports for user: {}", currentUserId);

        List<UserReport> reports = reportRepository.findByUserId(currentUserId);

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific report by user ID and report date.
     * Validates that the user has access to the requested report.
     * Implements cache-first strategy with MinIO.
     *
     * @param requestedUserId the user ID from the request
     * @param reportDate the report date
     * @param currentUserId the ID of the authenticated user from JWT token
     * @return the report response or null if not found
     * @throws UnauthorizedAccessException if user tries to access another user's report
     */
    public ReportResponse getReportByUserIdAndDate(Long requestedUserId, LocalDate reportDate, Long currentUserId) {
        logger.debug("Fetching report for user {} on date {} (authenticated user: {})",
                requestedUserId, reportDate, currentUserId);

        // Authorization check: users can only access their own reports
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access report for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }

        // Try to get from cache first
        String cacheKey = minioReportService.generateReportByDateKey(requestedUserId, reportDate.toString());
        ReportResponse cachedReport = tryGetFromCache(cacheKey);

        if (cachedReport != null) {
            logger.info("Cache HIT for report by date: user={}, date={}", requestedUserId, reportDate);
            return cachedReport;
        }

        logger.info("Cache MISS for report by date: user={}, date={}", requestedUserId, reportDate);

        // Cache miss - query OLAP database
        Optional<UserReport> report = reportRepository.findByUserIdAndReportDate(requestedUserId, reportDate);

        return report.map(r -> {
            ReportResponse response = mapToResponse(r);
            // Store in cache for future requests
            storeInCache(cacheKey, response);
            return response;
        }).orElse(null);
    }

    /**
     * Retrieves the latest report for the authenticated user.
     * Implements cache-first strategy with MinIO.
     *
     * @param requestedUserId the user ID from the request
     * @param currentUserId the ID of the authenticated user from JWT token
     * @return the report response or null if not found
     * @throws UnauthorizedAccessException if user tries to access another user's report
     */
    public ReportResponse getLatestReport(Long requestedUserId, Long currentUserId) {
        logger.debug("Fetching latest report for user {} (authenticated user: {})",
                requestedUserId, currentUserId);

        // Authorization check: users can only access their own reports
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access report for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }

        // Try to get from cache first
        String cacheKey = minioReportService.generateLatestReportKey(requestedUserId);
        ReportResponse cachedReport = tryGetFromCache(cacheKey);

        if (cachedReport != null) {
            logger.info("Cache HIT for latest report: user={}", requestedUserId);
            return cachedReport;
        }

        logger.info("Cache MISS for latest report: user={}", requestedUserId);

        // Cache miss - query OLAP database
        Optional<UserReport> report = reportRepository.findLatestByUserId(requestedUserId);

        return report.map(r -> {
            ReportResponse response = mapToResponse(r);
            // Store in cache for future requests
            storeInCache(cacheKey, response);
            return response;
        }).orElse(null);
    }

    /**
     * Retrieves a limited number of recent reports for the authenticated user.
     *
     * @param requestedUserId the user ID from the request
     * @param currentUserId the ID of the authenticated user from JWT token
     * @param limit maximum number of reports to return
     * @return list of report responses
     * @throws UnauthorizedAccessException if user tries to access another user's report
     */
    public List<ReportResponse> getRecentReports(Long requestedUserId, Long currentUserId, int limit) {
        logger.debug("Fetching {} recent reports for user {} (authenticated user: {})",
                limit, requestedUserId, currentUserId);

        // Authorization check: users can only access their own reports
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access reports for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access these reports");
        }

        List<UserReport> reports = reportRepository.findLatestByUserId(requestedUserId, limit);

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all reports from the database (admin functionality).
     * Should be protected by role-based access control.
     *
     * @return list of all report responses
     */
    public List<ReportResponse> getAllReports() {
        logger.debug("Fetching all reports");

        List<UserReport> reports = reportRepository.findAll();

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Attempts to retrieve a report from MinIO cache.
     * Returns null if cache is unavailable or report not found.
     *
     * @param cacheKey the cache key for the report
     * @return the cached report or null if not found
     */
    private ReportResponse tryGetFromCache(String cacheKey) {
        try {
            return minioReportService.getReport(cacheKey).orElse(null);
        } catch (MinioStorageException e) {
            logger.warn("Failed to retrieve from cache, falling back to database: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Stores a report in MinIO cache.
     * Failures are logged but don't affect the response.
     *
     * @param cacheKey the cache key for the report
     * @param report the report to cache
     */
    private void storeInCache(String cacheKey, ReportResponse report) {
        try {
            minioReportService.storeReport(cacheKey, report);
            logger.debug("Report stored in cache: {}", cacheKey);
        } catch (MinioStorageException e) {
            logger.warn("Failed to store report in cache: {}", e.getMessage());
            // Don't propagate exception - caching failure shouldn't affect response
        }
    }

    /**
     * Maps UserReport entity to ReportResponse DTO.
     * Converts Float values to Double for API consistency.
     */
    private ReportResponse mapToResponse(UserReport report) {
        return ReportResponse.builder()
                .userId(report.getUserId())
                .reportDate(report.getReportDate() != null ? report.getReportDate().toString() : null)
                .totalSessions(report.getTotalSessions())
                .avgSignalAmplitude(report.getAvgSignalAmplitude() != null ? 
                        report.getAvgSignalAmplitude().doubleValue() : null)
                .maxSignalAmplitude(report.getMaxSignalAmplitude() != null ? 
                        report.getMaxSignalAmplitude().doubleValue() : null)
                .minSignalAmplitude(report.getMinSignalAmplitude() != null ? 
                        report.getMinSignalAmplitude().doubleValue() : null)
                .avgSignalFrequency(report.getAvgSignalFrequency() != null ? 
                        report.getAvgSignalFrequency().doubleValue() : null)
                .totalUsageHours(report.getTotalUsageHours() != null ? 
                        report.getTotalUsageHours().doubleValue() : null)
                .prosthesisType(report.getProsthesisType())
                .muscleGroup(report.getMuscleGroup())
                .customerInfo(ReportResponse.CustomerInfo.builder()
                        .name(report.getCustomerName())
                        .email(report.getCustomerEmail())
                        .age(report.getCustomerAge())
                        .gender(report.getCustomerGender())
                        .country(report.getCustomerCountry())
                        .build())
                .build();
    }
}
