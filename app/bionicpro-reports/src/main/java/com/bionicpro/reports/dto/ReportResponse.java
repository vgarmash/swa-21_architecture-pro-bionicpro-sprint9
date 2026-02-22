package com.bionicpro.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object для API ответов, содержащих информацию об отчете пользователя.
 * Соответствует спецификации task2/impl/03_reports_api_service.md.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    /**
     * ID пользователя, которому принадлежит этот отчет
     */
    private Long userId;

    /**
     * Дата отчета в формате ISO (yyyy-MM-dd)
     */
    private String reportDate;

    /**
     * Общее количество записанных сессий сигнала ЭМГ
     */
    private Integer totalSessions;

    /**
     * Средняя амплитуда сигнала по всем сессиям
     */
    private Double avgSignalAmplitude;

    /**
     * Максимальная записанная амплитуда сигнала
     */
    private Double maxSignalAmplitude;

    /**
     * Минимальная записанная амплитуда сигнала
     */
    private Double minSignalAmplitude;

    /**
     * Средняя частота сигнала в Гц
     */
    private Double avgSignalFrequency;

    /**
     * Общее время использования в часах
     */
    private Double totalUsageHours;

    /**
     * Тип протеза (например, "upper_limb", "lower_limb")
     */
    private String prosthesisType;

    /**
     * Группа мышц, которая отслеживается (например, "biceps", "quadriceps")
     */
    private String muscleGroup;

    /**
     * Информация о клиенте из CRM
     */
    private CustomerInfo customerInfo;

    /**
     * Вложенный DTO для информации о клиенте
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        /**
         * Полное имя клиента
         */
        private String name;

        /**
         * Email клиента
         */
        private String email;

        /**
         * Возраст клиента
         */
        private Integer age;

        /**
         * Пол клиента
         */
        private String gender;

        /**
         * Страна клиента
         */
        private String country;
    }
}
