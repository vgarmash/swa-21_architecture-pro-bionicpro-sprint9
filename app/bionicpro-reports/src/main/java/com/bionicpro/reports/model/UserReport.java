package com.bionicpro.reports.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Сущность, представляющая отчет пользователя, хранящийся в ClickHouse.
 * Соответствует таблице user_reports в OLAP базе данных.
 *
 * Схема соответствует таблице, созданной ETL DAG:
 * - user_id UInt32
 * - report_date Date
 * - total_sessions UInt32
 * - avg_signal_amplitude Float32
 * - max_signal_amplitude Float32
 * - min_signal_amplitude Float32
 * - avg_signal_frequency Float32
 * - total_usage_hours Float32
 * - prosthesis_type String
 * - muscle_group String
 * - customer_name String
 * - customer_email String
 * - customer_age UInt8
 * - customer_gender String
 * - customer_country String
 * - created_at DateTime
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReport {

    /**
     * ID пользователя (соответствует ID пользователя Keycloak)
     */
    private Long userId;

    /**
     * Дата отчета
     */
    private LocalDate reportDate;

    /**
     * Общее количество записанных сессий сигнала ЭМГ
     */
    private Integer totalSessions;

    /**
     * Средняя амплитуда сигнала по всем сессиям
     */
    private Float avgSignalAmplitude;

    /**
     * Максимальная записанная амплитуда сигнала
     */
    private Float maxSignalAmplitude;

    /**
     * Минимальная записанная амплитуда сигнала
     */
    private Float minSignalAmplitude;

    /**
     * Средняя частота сигнала в Гц
     */
    private Float avgSignalFrequency;

    /**
     * Общее время использования в часах
     */
    private Float totalUsageHours;

    /**
     * Тип протеза (например, "upper_limb", "lower_limb")
     */
    private String prosthesisType;

    /**
     * Группа мышц, которая отслеживается (например, "biceps", "quadriceps")
     */
    private String muscleGroup;

    /**
     * Полное имя клиента из CRM
     */
    private String customerName;

    /**
     * Email клиента из CRM
     */
    private String customerEmail;

    /**
     * Возраст клиента из CRM
     */
    private Integer customerAge;

    /**
     * Пол клиента из CRM
     */
    private String customerGender;

    /**
     * Страна клиента из CRM
     */
    private String customerCountry;

    /**
     * Временная метка создания отчета в ClickHouse
     */
    private LocalDateTime createdAt;
}
