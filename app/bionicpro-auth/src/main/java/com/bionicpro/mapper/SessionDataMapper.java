package com.bionicpro.mapper;

import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.model.SessionData;
import org.mapstruct.*;

import java.time.Instant;

/**
 * MapStruct маппер для преобразований SessionData.
 * Предоставляет типобезопасное маппинг между SessionData и DTO ответа.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface SessionDataMapper {

    /**
     * Маппит SessionData в AuthStatusResponse для аутентифицированных пользователей.
     *
     * @param session исходный SessionData
     * @return AuthStatusResponse с authenticated=true
     */
    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "sessionExpiresAt", source = "expiresAt")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "roles", source = "roles")
    AuthStatusResponse toAuthStatusResponse(SessionData session);

    /**
     * Создаёт неаутентифицированный ответ.
     *
     * @return AuthStatusResponse с authenticated=false
     */
    default AuthStatusResponse toUnauthenticatedResponse() {
        return AuthStatusResponse.builder()
                .authenticated(false)
                .build();
    }

    /**
     * Копирует SessionData с новым ID сессии и обновлённым lastAccessedAt.
     * Используется для ротации сессии.
     *
     * @param source оригинальный SessionData
     * @param newSessionId новый ID сессии
     * @return новый SessionData с обновлёнными полями, или null, если source равен null
     */
    default SessionData copyForRotation(SessionData source, String newSessionId) {
        if (source == null) {
            return null;
        }
        return copyForRotationInternal(source, newSessionId);
    }

    /**
     * Внутренний метод для копирования SessionData с новым ID сессии.
     * Не использовать напрямую - используйте copyForRotation.
     */
    @Mapping(target = "sessionId", source = "newSessionId")
    @Mapping(target = "lastAccessedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "userId", source = "source.userId")
    @Mapping(target = "username", source = "source.username")
    @Mapping(target = "roles", source = "source.roles")
    @Mapping(target = "accessToken", source = "source.accessToken")
    @Mapping(target = "refreshToken", source = "source.refreshToken")
    @Mapping(target = "accessTokenExpiresAt", source = "source.accessTokenExpiresAt")
    @Mapping(target = "refreshTokenExpiresAt", source = "source.refreshTokenExpiresAt")
    @Mapping(target = "createdAt", source = "source.createdAt")
    @Mapping(target = "expiresAt", source = "source.expiresAt")
    SessionData copyForRotationInternal(SessionData source, String newSessionId);
}