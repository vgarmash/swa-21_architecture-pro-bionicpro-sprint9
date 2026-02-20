package com.bionicpro.mapper;

import com.bionicpro.audit.AuditEvent;
import com.bionicpro.audit.AuditEventType;
import org.mapstruct.*;

import java.time.Instant;

/**
 * MapStruct mapper for AuditEvent creation.
 * Note: This mapper provides base mapping - sanitization logic
 * should remain in AuditServiceImpl for security reasons.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    imports = {Instant.class}
)
public interface AuditEventMapper {

    /**
     * Creates base AuditEvent with timestamp.
     * Used as starting point - sanitization applied in service layer.
     *
     * @param eventType the audit event type
     * @param principal the user ID
     * @param sessionId the session ID
     * @param correlationId the correlation ID from MDC
     * @param clientIp the client IP address
     * @param userAgent the user agent string
     * @param outcome the outcome (SUCCESS, FAILURE, EXPIRED)
     * @return AuditEvent with populated fields
     */
    @Mapping(target = "timestamp", expression = "java(Instant.now())")
    @Mapping(target = "details", expression = "java(new java.util.HashMap<>())")
    AuditEvent createAuditEvent(
        AuditEventType eventType,
        String principal,
        String sessionId,
        String correlationId,
        String clientIp,
        String userAgent,
        String outcome
    );
}
