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
 * Сервисный слой для операций с отчётами с проверками авторизации.
 * Гарантирует, что пользователи могут получить доступ только к своим собственным отчётам.
 * Интегрируется с MinIO для кэширования для уменьшения нагрузки на OLAP базу данных.
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
     * Получает все отчёты для аутентифицированного пользователя.
     *
     * @param currentUserId идентификатор аутентифицированного пользователя из JWT токена
     * @return список ответов с отчётами для пользователя
     */
    public List<ReportResponse> getReportsForUser(Long currentUserId) {
        logger.debug("Fetching reports for user: {}", currentUserId);

        List<UserReport> reports = reportRepository.findByUserId(currentUserId);

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Получает конкретный отчёт по ID пользователя и дате.
     * Проверяет, что пользователь имеет доступ к запрошенному отчёту.
     * Реализует стратегию кэширования сначала MinIO.
     *
     * @param requestedUserId ID пользователя из запроса
     * @param reportDate дата отчёта
     * @param currentUserId идентификатор аутентифицированного пользователя из JWT токена
     * @return ответ с отчётом или null, если не найден
     * @throws UnauthorizedAccessException если пользователь пытается получить доступ к отчёту другого пользователя
     */
    public ReportResponse getReportByUserIdAndDate(Long requestedUserId, LocalDate reportDate, Long currentUserId) {
        logger.debug("Fetching report for user {} on date {} (authenticated user: {})",
                requestedUserId, reportDate, currentUserId);

        // Проверка авторизации: пользователи могут получить доступ только к своим собственным отчётам
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access report for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }

        // Сначала пробуем получить из кэша
        String cacheKey = minioReportService.generateReportByDateKey(requestedUserId, reportDate.toString());
        ReportResponse cachedReport = tryGetFromCache(cacheKey);

        if (cachedReport != null) {
            logger.info("Cache HIT for report by date: user={}, date={}", requestedUserId, reportDate);
            return cachedReport;
        }

        logger.info("Cache MISS for report by date: user={}, date={}", requestedUserId, reportDate);

        // Промах кэша - запрашиваем OLAP базу данных
        Optional<UserReport> report = reportRepository.findByUserIdAndReportDate(requestedUserId, reportDate);

        return report.map(r -> {
            ReportResponse response = mapToResponse(r);
            // Сохраняем в кэш для будущих запросов
            storeInCache(cacheKey, response);
            return response;
        }).orElse(null);
    }

    /**
     * Получает последний отчёт для аутентифицированного пользователя.
     * Реализует стратегию кэширования сначала MinIO.
     *
     * @param requestedUserId ID пользователя из запроса
     * @param currentUserId идентификатор аутентифицированного пользователя из JWT токена
     * @return ответ с отчётом или null, если не найден
     * @throws UnauthorizedAccessException если пользователь пытается получить доступ к отчёту другого пользователя
     */
    public ReportResponse getLatestReport(Long requestedUserId, Long currentUserId) {
        logger.debug("Fetching latest report for user {} (authenticated user: {})",
                requestedUserId, currentUserId);

        // Проверка авторизации: пользователи могут получить доступ только к своим собственным отчётам
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access report for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }

        // Сначала пробуем получить из кэша
        String cacheKey = minioReportService.generateLatestReportKey(requestedUserId);
        ReportResponse cachedReport = tryGetFromCache(cacheKey);

        if (cachedReport != null) {
            logger.info("Cache HIT for latest report: user={}", requestedUserId);
            return cachedReport;
        }

        logger.info("Cache MISS for latest report: user={}", requestedUserId);

        // Промах кэша - запрашиваем OLAP базу данных
        Optional<UserReport> report = reportRepository.findLatestByUserId(requestedUserId);

        return report.map(r -> {
            ReportResponse response = mapToResponse(r);
            // Сохраняем в кэш для будущих запросов
            storeInCache(cacheKey, response);
            return response;
        }).orElse(null);
    }

    /**
     * Получает ограниченное количество последних отчётов для аутентифицированного пользователя.
     *
     * @param requestedUserId ID пользователя из запроса
     * @param currentUserId идентификатор аутентифицированного пользователя из JWT токена
     * @param limit максимальное количество отчётов для возврата
     * @return список ответов с отчётами
     * @throws UnauthorizedAccessException если пользователь пытается получить доступ к отчётам другого пользователя
     */
    public List<ReportResponse> getRecentReports(Long requestedUserId, Long currentUserId, int limit) {
        logger.debug("Fetching {} recent reports for user {} (authenticated user: {})",
                limit, requestedUserId, currentUserId);

        // Проверка авторизации: пользователи могут получить доступ только к своим собственным отчётам
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
     * Получает все отчёты из базы данных (функционал администратора).
     * Должен быть защищён контролем доступа на основе ролей.
     *
     * @return список всех ответов с отчётами
     */
    public List<ReportResponse> getAllReports() {
        logger.debug("Fetching all reports");

        List<UserReport> reports = reportRepository.findAll();

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Пытается получить отчёт из кэша MinIO.
     * Возвращает null, если кэш недоступен или отчёт не найден.
     *
     * @param cacheKey ключ кэша для отчёта
     * @return кэшированный отчёт или null, если не найден
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
     * Сохраняет отчёт в кэш MinIO.
     * Ошибки логируются, но не влияют на ответ.
     *
     * @param cacheKey ключ кэша для отчёта
     * @param report отчёт для кэширования
     */
    private void storeInCache(String cacheKey, ReportResponse report) {
        try {
            minioReportService.storeReport(cacheKey, report);
            logger.debug("Report stored in cache: {}", cacheKey);
        } catch (MinioStorageException e) {
            logger.warn("Failed to store report in cache: {}", e.getMessage());
            // Не пробрасываем исключение - ошибка кэширования не должна влиять на ответ
        }
    }

    /**
     * Преобразует сущность UserReport в DTO ReportResponse.
     * Конвертирует значения Float в Double для согласованности API.
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
