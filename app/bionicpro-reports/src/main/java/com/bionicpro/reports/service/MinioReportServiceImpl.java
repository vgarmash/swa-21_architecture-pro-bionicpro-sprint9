package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.MinioStorageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of MinIO report caching service.
 * Handles storage, retrieval, and management of cached reports in MinIO object storage.
 */
@Service
public class MinioReportServiceImpl implements MinioReportService {

    private static final Logger logger = LoggerFactory.getLogger(MinioReportServiceImpl.class);
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String KEY_PREFIX = "user-reports";

    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${app.minio.bucket-name:reports}")
    private String bucketName;

    @Value("${app.minio.cdn-base-url:http://localhost:9000}")
    private String cdnBaseUrl;

    @Value("${app.minio.presigned-url-expiry-hours:168}")
    private int presignedUrlExpiryHours;

    @Value("${minio.enabled:true}")
    private boolean minioEnabled;

    public MinioReportServiceImpl(
            @Value("${app.minio.endpoint:http://minio:9000}") String endpoint,
            @Value("${app.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.minio.secret-key:minioadmin}") String secretKey) {
        
        logger.info("Initializing MinIO client with endpoint: {}", endpoint);
        
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    @Override
    public void initializeBucket() {
        if (minioEnabled) {
            try {
                boolean bucketExists = minioClient.bucketExists(
                        BucketExistsArgs.builder()
                                .bucket(bucketName)
                                .build()
                );

                if (!bucketExists) {
                    logger.info("Creating MinIO bucket: {}", bucketName);
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(bucketName)
                                    .build()
                    );
                    logger.info("MinIO bucket created successfully: {}", bucketName);
                } else {
                    logger.debug("MinIO bucket already exists: {}", bucketName);
                }
            } catch (ErrorResponseException | InsufficientDataException | InternalException |
                     InvalidKeyException | InvalidResponseException | IOException |
                     NoSuchAlgorithmException | ServerException | XmlParserException e) {
                logger.error("Failed to initialize MinIO bucket: {}", e.getMessage());
                throw new MinioStorageException("Failed to initialize MinIO bucket", e);
            }
        }
    }

    @Override
    public boolean reportExists(String objectKey) {
        try {
            logger.debug("Checking if report exists: {}", objectKey);
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            logger.debug("Report exists: {}", objectKey);
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                logger.debug("Report does not exist: {}", objectKey);
                return false;
            }
            logger.error("Error checking report existence: {}", e.getMessage());
            throw new MinioStorageException("Error checking report existence", e);
        } catch (InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException |
                 ServerException | XmlParserException e) {
            logger.error("Error checking report existence: {}", e.getMessage());
            throw new MinioStorageException("Error checking report existence", e);
        }
    }

    @Override
    public void storeReport(String objectKey, ReportResponse report) {
        try {
            logger.debug("Storing report: {}", objectKey);
            String jsonContent = objectMapper.writeValueAsString(report);
            byte[] contentBytes = jsonContent.getBytes();
            
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(contentBytes), contentBytes.length, -1)
                            .contentType(CONTENT_TYPE_JSON)
                            .build()
            );
            
            logger.info("Report stored successfully: {} ({} bytes)", objectKey, contentBytes.length);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize report: {}", e.getMessage());
            throw new MinioStorageException("Failed to serialize report", e);
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            logger.error("Failed to store report: {}", e.getMessage());
            throw new MinioStorageException("Failed to store report", e);
        }
    }

    @Override
    public Optional<ReportResponse> getReport(String objectKey) {
        try {
            logger.debug("Retrieving report: {}", objectKey);
            
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            )) {
                byte[] content = stream.readAllBytes();
                String jsonContent = new String(content);
                ReportResponse report = objectMapper.readValue(jsonContent, ReportResponse.class);
                logger.debug("Report retrieved successfully: {}", objectKey);
                return Optional.of(report);
            }
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                logger.debug("Report not found: {}", objectKey);
                return Optional.empty();
            }
            logger.error("Error retrieving report: {}", e.getMessage());
            throw new MinioStorageException("Error retrieving report", e);
        } catch (InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException |
                 ServerException | XmlParserException e) {
            logger.error("Error retrieving report: {}", e.getMessage());
            throw new MinioStorageException("Error retrieving report", e);
        }
    }

    @Override
    public String getCdnUrl(String objectKey) {
        try {
            logger.debug("Generating CDN URL for: {}", objectKey);
            
            // Generate a presigned URL for direct access
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(presignedUrlExpiryHours, TimeUnit.HOURS)
                            .build()
            );
            
            logger.debug("Generated presigned URL: {}", presignedUrl);
            return presignedUrl;
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            logger.error("Failed to generate CDN URL: {}", e.getMessage());
            // Fallback to direct URL construction
            String fallbackUrl = String.format("%s/%s/%s", cdnBaseUrl, bucketName, objectKey);
            logger.debug("Using fallback URL: {}", fallbackUrl);
            return fallbackUrl;
        }
    }

    @Override
    public void deleteReport(String objectKey) {
        try {
            logger.debug("Deleting report: {}", objectKey);
            
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            
            logger.info("Report deleted successfully: {}", objectKey);
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            logger.error("Failed to delete report: {}", e.getMessage());
            throw new MinioStorageException("Failed to delete report", e);
        }
    }

    @Override
    public void deleteUserReports(Long userId) {
        try {
            logger.debug("Deleting all reports for user: {}", userId);
            
            String userPrefix = String.format("%s/%d/", KEY_PREFIX, userId);
            
            // List all objects with the user prefix
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(userPrefix)
                            .recursive(true)
                            .build()
            );
            
            // Delete each object
            for (Result<Item> result : results) {
                Item item = result.get();
                deleteReport(item.objectName());
            }
            
            logger.info("All reports deleted for user: {}", userId);
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            logger.error("Failed to delete user reports: {}", e.getMessage());
            throw new MinioStorageException("Failed to delete user reports", e);
        }
    }

    @Override
    public String generateLatestReportKey(Long userId) {
        return String.format("%s/%d/latest.json", KEY_PREFIX, userId);
    }

    @Override
    public String generateReportByDateKey(Long userId, String reportDate) {
        return String.format("%s/%d/%s.json", KEY_PREFIX, userId, reportDate);
    }

    @Override
    public String generateHistoryReportKey(Long userId, String reportDate) {
        return String.format("%s/%d/history/%s.json", KEY_PREFIX, userId, reportDate);
    }
}
