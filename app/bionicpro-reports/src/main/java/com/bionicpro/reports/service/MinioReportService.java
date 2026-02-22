package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import java.util.Optional;

/**
 * Интерфейс сервиса для операций кэширования отчетов в MinIO.
 * Предоставляет методы для хранения, получения и управления кэшированными отчетами в объектном хранилище MinIO.
 */
public interface MinioReportService {

    /**
     * Проверяет, существует ли отчет в хранилище MinIO.
     *
     * @param objectKey ключ объекта в MinIO
     * @return true, если отчет существует, false в противном случае
     */
    boolean reportExists(String objectKey);

    /**
     * Сохраняет отчет в MinIO в формате JSON.
     *
     * @param objectKey ключ объекта в MinIO
     * @param report данные отчета для сохранения
     * @throws MinioStorageException если операция сохранения не удалась
     */
    void storeReport(String objectKey, ReportResponse report);

    /**
     * Получает отчет из хранилища MinIO.
     *
     * @param objectKey ключ объекта в MinIO
     * @return Optional, содержащий отчет, если он найден, пустой в противном случае
     * @throws MinioStorageException если операция получения не удалась
     */
    Optional<ReportResponse> getReport(String objectKey);

    /**
     * Генерирует CDN URL для доступа к отчету.
     * Возвращает предварительно подписанный URL, который можно использовать для прямого доступа к объекту.
     *
     * @param objectKey ключ объекта в MinIO
     * @return CDN URL для отчета
     */
    String getCdnUrl(String objectKey);

    /**
     * Удаляет конкретный отчет из MinIO.
     *
     * @param objectKey ключ объекта в MinIO
     * @throws MinioStorageException если операция удаления не удалась
     */
    void deleteReport(String objectKey);

    /**
     * Удаляет все кэшированные отчеты для указанного пользователя.
     *
     * @param userId ID пользователя, отчеты которого нужно удалить
     * @throws MinioStorageException если операция удаления не удалась
     */
    void deleteUserReports(Long userId);

    /**
     * Генерирует ключ объекта для последнего отчета пользователя.
     *
     * @param userId ID пользователя
     * @return путь ключа объекта
     */
    String generateLatestReportKey(Long userId);

    /**
     * Генерирует ключ объекта для отчета по дате.
     *
     * @param userId ID пользователя
     * @param reportDate дата отчета в формате ISO (yyyy-MM-dd)
     * @return путь ключа объекта
     */
    String generateReportByDateKey(Long userId, String reportDate);

    /**
     * Генерирует ключ объекта для исторического отчета.
     *
     * @param userId ID пользователя
     * @param reportDate дата отчета в формате ISO (yyyy-MM-dd)
     * @return путь ключа объекта
     */
    String generateHistoryReportKey(Long userId, String reportDate);

    /**
     * Убеждается, что корзина MinIO существует, создавая её при необходимости.
     * Вызывается при запуске приложения.
     */
    void initializeBucket();
}
