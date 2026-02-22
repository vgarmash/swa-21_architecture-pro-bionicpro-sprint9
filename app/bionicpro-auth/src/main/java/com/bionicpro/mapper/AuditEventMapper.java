package com.bionicpro.mapper;

import com.bionicpro.audit.AuditEvent;
import com.bionicpro.audit.AuditEventType;
import org.mapstruct.*;

import java.time.Instant;

/**
 * MapStruct маппер для создания AuditEvent.
 * Примечание: Этот маппер предоставляет базовое маппинг - логика санитации
 * должна оставаться в AuditServiceImpl по соображениям безопасности.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    imports = {Instant.class}
)
public interface AuditEventMapper {

    /**
     * Создаёт базовый AuditEvent с временной меткой.
     * Используется как отправная точка - санитация применяется в слое сервиса.
     *
     * @param eventType тип события аудита
     * @param principal ID пользователя
     * @param sessionId ID сессии
     * @param correlationId корреляционный ID из MDC
     * @param clientIp IP адрес клиента
     * @param userAgent строка user agent
     * @param outcome результат (SUCCESS, FAILURE, EXPIRED)
     * @return AuditEvent с заполненными полями
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
