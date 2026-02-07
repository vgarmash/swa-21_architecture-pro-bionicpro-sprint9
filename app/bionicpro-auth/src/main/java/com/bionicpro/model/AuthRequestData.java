package com.bionicpro.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Данные OAuth2 запроса аутентификации, временно хранимые в Redis по ключу state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequestData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String redirectUri;
    private String codeVerifier;
    private String nonce;
    private Instant createdAt;
}
