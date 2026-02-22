package com.bionicpro.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Модель данных токена для OAuth2 токенов.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accessToken;
    private String refreshToken;
    private Instant accessTokenExpiresAt;
    private Instant refreshTokenExpiresAt;
    private String tokenType; // Bearer
    private String scope;
}
