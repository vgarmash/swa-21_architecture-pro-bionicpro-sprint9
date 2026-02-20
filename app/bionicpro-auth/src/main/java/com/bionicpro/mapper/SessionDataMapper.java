package com.bionicpro.mapper;

import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.model.SessionData;
import org.mapstruct.*;

import java.time.Instant;

/**
 * MapStruct mapper for SessionData transformations.
 * Provides type-safe mapping between SessionData and response DTOs.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface SessionDataMapper {

    /**
     * Maps SessionData to AuthStatusResponse for authenticated users.
     * 
     * @param session the source SessionData
     * @return AuthStatusResponse with authenticated=true
     */
    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "sessionExpiresAt", source = "expiresAt")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "roles", source = "roles")
    AuthStatusResponse toAuthStatusResponse(SessionData session);

    /**
     * Creates an unauthenticated response.
     * 
     * @return AuthStatusResponse with authenticated=false
     */
    default AuthStatusResponse toUnauthenticatedResponse() {
        return AuthStatusResponse.builder()
                .authenticated(false)
                .build();
    }

    /**
     * Copies SessionData with a new session ID and updated lastAccessedAt.
     * Used for session rotation.
     * 
     * @param source the original SessionData
     * @param newSessionId the new session ID
     * @return new SessionData with updated fields, or null if source is null
     */
    default SessionData copyForRotation(SessionData source, String newSessionId) {
        if (source == null) {
            return null;
        }
        return copyForRotationInternal(source, newSessionId);
    }

    /**
     * Internal method for copying SessionData with a new session ID.
     * Do not use directly - use copyForRotation instead.
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