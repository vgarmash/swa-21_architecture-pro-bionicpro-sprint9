package com.bionicpro.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Модель данных сессии, хранимой в Redis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String userId;
    private String username;
    
    @JsonIgnore
    private List<String> roles;
    
    @JsonIgnore
    private String accessToken;
    
    @JsonIgnore
    private String refreshToken;
    
    private Instant accessTokenExpiresAt;
    private Instant refreshTokenExpiresAt;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastAccessedAt;
}
