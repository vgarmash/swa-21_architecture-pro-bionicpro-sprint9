package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
import com.bionicpro.reports.model.UserReport;
import com.bionicpro.reports.repository.ReportRepositoryCdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервисный слой для операций отчётов CDC с проверками авторизации.
 * Гарантирует, что пользователи могут получить доступ только к своим собственным отчётам.
 * Работает непосредственно с таблицей CDC без кэширования (стратегия кэширования не требуется).
 */
@Service
public class ReportServiceCdc {

    private static final Logger logger = LoggerFactory.getLogger(ReportServiceCdc.class);

    private final ReportRepositoryCdc reportRepositoryCdc;

    public ReportServiceCdc(ReportRepositoryCdc reportRepositoryCdc) {
        this.reportRepositoryCdc = reportRepositoryCdc;
    }

    /**
     * Получает все отчёты для аутентифицированного пользователя из таблицы CDC.
     *
     * @param currentUserId идентификатор аутентифицированного пользователя из JWT токена
     * @return список ответов с отчётами для пользователя
     */
    public List<ReportResponse> getReportsForUser(Long currentUserId) {
        logger.debug("Fetching CDC reports for user: {}", currentUserId);

        List<UserReport> reports = reportRepositoryCdc.findByUserId(currentUserId);

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Получает конкретный отчёт по ID пользователя и дате из таблицы CDC.
     * Проверяет, что пользователь имеет доступ к запрошенному отчёту.
     *
     * @param requestedUserId ID пользователя из запроса
     * @param reportDate дата отчёта
     * @param currentUserId идентификатор аутентифицированного пользователя из JWT токена
     * @return ответ с отчётом или null, если не найден
     * @throws UnauthorizedAccessException если пользователь пытается получить доступ к отчёту другого пользователя
     */
    public ReportResponse getReportByUserIdAndDate(Long requestedUserId, LocalDate reportDate, Long currentUserId) {
        logger.debug("Fetching CDC report for user {} on date {} (authenticated user: {})",
                requestedUserId, reportDate, currentUserId);

        // Проверка авторизации: пользователи могут получить доступ только к своим собственным отчётам
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access CDC report for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }

        // Запрашиваем базу данных CDC напрямую (без кэширования)
        Optional<UserReport> report = reportRepositoryCdc.findByUserIdAndReportDate(requestedUserId, reportDate);

        return report.map(this::mapToResponse).orElse(null);
    }

    /**
     * Получает последний отчёт для аутентифицированного пользователя из таблицы CDC.
     *
     * @param requestedUserId ID пользователя из запроса
     * @param currentUserId идентификатор аутентифицированного пользователя из JWT токена
     * @return ответ с отчётом или null, если не найден
     * @throws UnauthorizedAccessException если пользователь пытается получить доступ к отчёту другого пользователя
     */
    public ReportResponse getLatestReport(Long requestedUserId, Long currentUserId) {
        logger.debug("Fetching latest CDC report for user {} (authenticated user: {})",
                requestedUserId, currentUserId);

        // Проверка авторизации: пользователи могут получить доступ только к своим собственным отчётам
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access CDC report for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }

        // Запрашиваем базу данных CDC напрямую (без кэширования)
        Optional<UserReport> report = reportRepositoryCdc.findLatestByUserId(requestedUserId);

        return report.map(this::mapToResponse).orElse(null);
    }

    /**
     * Получает ограниченное количество последних отчётов для аутентифицированного пользователя из таблицы CDC.
     *
     * @param requestedUserId ID пользователя из запроса
     * @param currentUserId идентификатор аутентифицированного пользователя из JWT токена
     * @param limit максимальное количество отчётов для возврата
     * @return список ответов с отчётами
     * @throws UnauthorizedAccessException если пользователь пытается получить доступ к отчётам другого пользователя
     */
    public List<ReportResponse> getRecentReports(Long requestedUserId, Long currentUserId, int limit) {
        logger.debug("Fetching {} recent CDC reports for user {} (authenticated user: {})",
                limit, requestedUserId, currentUserId);

        // Проверка авторизации: пользователи могут получить доступ только к своим собственным отчётам
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access CDC reports for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access these reports");
        }

        List<UserReport> reports = reportRepositoryCdc.findLatestByUserId(requestedUserId, limit);

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Получает все отчёты из таблицы CDC (функционал администратора).
     * Должен быть защищён контролем доступа на основе ролей.
     *
     * @return список всех ответов с отчётами
     */
    public List<ReportResponse> getAllReports() {
        logger.debug("Fetching all CDC reports");

        List<UserReport> reports = reportRepositoryCdc.findAll();

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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