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
 * Audit event data class representing an authentication audit log entry.
 * Uses builder pattern for construction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Timestamp of the audit event.
     */
    private Instant timestamp;

    /**
     * Correlation ID for request tracing.
     */
    private String correlationId;

    /**
     * Type of audit event.
     */
    private AuditEventType eventType;

    /**
     * Principal (user ID) associated with the event.
     */
    private String principal;

    /**
     * Client IP address.
     */
    private String clientIp;

    /**
     * User agent string from the client.
     */
    private String userAgent;

    /**
     * Session ID associated with the event.
     */
    private String sessionId;

    /**
     * Outcome of the event (SUCCESS or FAILURE).
     */
    private String outcome;

    /**
     * Type of error (optional).
     */
    private String errorType;

    /**
     * Error message (sanitized, optional).
     */
    private String errorMessage;

    /**
     * Additional details for the audit event.
     */
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Pattern to detect sensitive data that should be sanitized.
     */
    private static final Pattern SENSITIVE_DATA_PATTERN = Pattern.compile(
            "(?i)(password|token|secret|key|authorization|bearer|api[_-]?key|credential)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Sanitizes the error message by removing potentially sensitive data.
     *
     * @param message the raw error message
     * @return sanitized message safe for logging
     */
    public static String sanitizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        // Replace potential sensitive values with placeholder
        String sanitized = message.replaceAll("(?i)([\\w-]+=[^\\s&]+)", "$1=[REDACTED]");
        
        // Remove common sensitive header patterns
        sanitized = sanitized.replaceAll("(?i)(Bearer\\s+)[\\w.-]+", "$1[REDACTED]");
        sanitized = sanitized.replaceAll("(?i)(Basic\\s+)[\\w.-]+", "$1[REDACTED]");
        
        return sanitized;
    }

    /**
     * Creates a new AuditEvent builder with default timestamp set to current instant.
     *
     * @return a new builder instance
     */
    public static AuditEventBuilder builder() {
        return new AuditEventBuilder().timestamp(Instant.now());
    }

    /**
     * Converts the audit event to a JSON-like string representation.
     * This is suitable for logging purposes.
     *
     * @return JSON-like string representation
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
     * Sanitizes a value for safe logging.
     *
     * @param value the value to sanitize
     * @return sanitized value or empty string if null
     */
    private static String sanitizeValue(String value) {
        if (value == null) {
            return "";
        }
        // Truncate if too long and remove newlines
        if (value.length() > 500) {
            return value.substring(0, 500).replaceAll("[\\r\\n]", " ") + "...[truncated]";
        }
        return value.replaceAll("[\\r\\n]", " ");
    }
}
