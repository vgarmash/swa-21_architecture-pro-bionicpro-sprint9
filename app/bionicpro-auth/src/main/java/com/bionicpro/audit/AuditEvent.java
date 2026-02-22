package com.bionicpro.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Класс данных события аудита, представляющий запись журнала аудита аутентификации.
 * Использует паттерн Builder для построения.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Временная метка события аудита.
     */
    private Instant timestamp;

    /**
     * Идентификатор корреляции для трассировки запросов.
     */
    private String correlationId;

    /**
     * Тип события аудита.
     */
    private AuditEventType eventType;

    /**
     * Principal (идентификатор пользователя), связанный с событием.
     */
    private String principal;

    /**
     * IP-адрес клиента.
     */
    private String clientIp;

    /**
     * Строка user agent от клиента.
     */
    private String userAgent;

    /**
     * Идентификатор сессии, связанный с событием.
     */
    private String sessionId;

    /**
     * Результат события (SUCCESS или FAILURE).
     */
    private String outcome;

    /**
     * Тип ошибки (опционально).
     */
    private String errorType;

    /**
     * Сообщение об ошибке (очищенное, опционально).
     */
    private String errorMessage;

    /**
     * Дополнительные детали для события аудита.
     */
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Паттерн для обнаружения конфиденциальных данных, которые должны быть очищены.
     */
    private static final Pattern SENSITIVE_DATA_PATTERN = Pattern.compile(
            "(?i)(password|token|secret|key|authorization|bearer|api[_-]?key|credential)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Очищает сообщение об ошибке, удаляя потенциально конфиденциальные данные.
     *
     * @param message сырое сообщение об ошибке
     * @return очищенное сообщение, безопасное для логирования
     */
    public static String sanitizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        // Заменяем потенциально конфиденциальные значения на заполнитель
        String sanitized = message.replaceAll("(?i)([\\w-]+=[^\\s&]+)", "$1=[REDACTED]");
        
        // Удаляем общие паттерны конфиденциальных заголовков
        sanitized = sanitized.replaceAll("(?i)(Bearer\\s+)[\\w.-]+", "$1[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)(Basic\\s+)[\\w.-]+", "$1[REDACTED]");
        
        return sanitized;
    }

    /**
     * Создаёт новый строитель AuditEvent с временной меткой по умолчанию, установленной на текущий момент.
     *
     * @return новый экземпляр строителя
     */
    public static AuditEventBuilder builder() {
        return new AuditEventBuilder().timestamp(Instant.now());
    }

    /**
     * Преобразует событие аудита в строковое представление в формате JSON.
     * Это подходит для целей логирования.
     *
     * @return строковое представление в формате JSON
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"timestamp\":\"").append(timestamp).append("\",");
        sb.append("\"correlationId\":\"").append(sanitizeValue(correlationId)).append("\",");
        sb.append("\"eventType\":\"").append(eventType).append("\",");
        sb.append("\"principal\":\"").append(sanitizeValue(principal)).append("\",");
        sb.append("\"clientIp\":\"").append(sanitizeValue(clientIp)).append("\",");
        sb.append("\"userAgent\":\"").append(sanitizeValue(userAgent)).append("\",");
        sb.append("\"sessionId\":\"").append(sanitizeValue(sessionId)).append("\",");
        sb.append("\"outcome\":\"").append(outcome).append("\"");
        
        if (errorType != null && !errorType.isBlank()) {
            sb.append(",\"errorType\":\"").append(sanitizeValue(errorType)).append("\"");
        }
        
        if (errorMessage != null && !errorMessage.isBlank()) {
            sb.append(",\"errorMessage\":\"").append(sanitizeErrorMessage(errorMessage)).append("\"");
        }
        
        if (details != null && !details.isEmpty()) {
            sb.append(",\"details\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : details.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(entry.getKey()).append("\":\"")
                  .append(sanitizeValue(String.valueOf(entry.getValue()))).append("\"");
                first = false;
            }
            sb.append("}");
        }
        
        sb.append("}");
        return sb.toString();
    }

    /**
     * Очищает значение для безопасного логирования.
     *
     * @param value значение для очистки
     * @return очищенное значение или пустая строка, если null
     */
    private static String sanitizeValue(String value) {
        if (value == null) {
            return "";
        }
        // Обрезаем, если слишком длинное, и удаляем переносы строк
        if (value.length() > 500) {
            return value.substring(0, 500).replaceAll("[\\r\\n]", " ") + "...[truncated]";
        }
        return value.replaceAll("[\\r\\n]", " ");
    }
}
