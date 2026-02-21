package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import java.util.Optional;

/**
 * Service interface for MinIO report caching operations.
 * Provides methods for storing, retrieving, and managing cached reports in MinIO object storage.
 */
public interface MinioReportService {

    /**
     * Checks if a report exists in MinIO storage.
     *
     * @param objectKey the object key path in MinIO
     * @return true if the report exists, false otherwise
     */
    boolean reportExists(String objectKey);

    /**
     * Stores a report in MinIO as JSON.
     *
     * @param objectKey the object key path in MinIO
     * @param report the report data to store
     * @throws MinioStorageException if storage operation fails
     */
    void storeReport(String objectKey, ReportResponse report);

    /**
     * Retrieves a report from MinIO storage.
     *
     * @param objectKey the object key path in MinIO
     * @return Optional containing the report if found, empty otherwise
     * @throws MinioStorageException if retrieval operation fails
     */
    Optional<ReportResponse> getReport(String objectKey);

    /**
     * Generates a CDN URL for accessing the report.
     * Returns a presigned URL that can be used to access the object directly.
     *
     * @param objectKey the object key path in MinIO
     * @return the CDN URL for the report
     */
    String getCdnUrl(String objectKey);

    /**
     * Deletes a specific report from MinIO.
     *
     * @param objectKey the object key path in MinIO
     * @throws MinioStorageException if deletion operation fails
     */
    void deleteReport(String objectKey);

    /**
     * Deletes all cached reports for a specific user.
     *
     * @param userId the user ID whose reports should be deleted
     * @throws MinioStorageException if deletion operation fails
     */
    void deleteUserReports(Long userId);

    /**
     * Generates the object key for a user's latest report.
     *
     * @param userId the user ID
     * @return the object key path
     */
    String generateLatestReportKey(Long userId);

    /**
     * Generates the object key for a report by date.
     *
     * @param userId the user ID
     * @param reportDate the report date in ISO format (yyyy-MM-dd)
     * @return the object key path
     */
    String generateReportByDateKey(Long userId, String reportDate);

    /**
     * Generates the object key for a historical report.
     *
     * @param userId the user ID
     * @param reportDate the report date in ISO format (yyyy-MM-dd)
     * @return the object key path
     */
    String generateHistoryReportKey(Long userId, String reportDate);

    /**
     * Ensures the MinIO bucket exists, creating it if necessary.
     * Called during application startup.
     */
    void initializeBucket();
}
