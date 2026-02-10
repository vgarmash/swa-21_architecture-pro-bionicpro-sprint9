/**
 * Сервис для взаимодействия с Keycloak для аутентификации и авторизации.
 * Обеспечивает обмен кода на токены, обновление токенов и выход пользователя.
 * Реализует PKCE для безопасности OAuth2.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.service;

import com.bionicpro.auth.model.PKCEParams;
import com.bionicpro.auth.model.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Base64;

@Service
public class KeycloakService {
    
    /**
     * URI для эмиттера Keycloak
     */
    @Value("${bff.keycloak.issuer-uri}")
    private String issuerUri;
    
    /**
     * URI для получения токенов Keycloak
     */
    @Value("${bff.keycloak.token-uri}")
    private String tokenUri;
    
    /**
     * URI для выхода из Keycloak
     */
    @Value("${bff.keycloak.logout-uri}")
    private String logoutUri;
    
    /**
     * ID клиента для Keycloak
     */
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;
    
    /**
     * Секрет клиента для Keycloak
     */
    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;
    
    /**
     * Шаблон REST клиента для выполнения HTTP запросов
     */
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * Генерирует URI для авторизации через Keycloak.
     * Использует PKCE для безопасности.
     *
     * @param pkceParams параметры PKCE
     * @param redirectUri URI перенаправления после авторизации
     * @return URI для авторизации через Keycloak
     */
    public URI getAuthorizationUri(PKCEParams pkceParams, String redirectUri) {
        String authorizationUri = issuerUri.replace("/realms/reports-realm", "") +
                                  "/realms/reports-realm/protocol/openid-connect/auth";
        
        return UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code_challenge", pkceParams.getCodeChallenge())
                .queryParam("code_challenge_method", pkceParams.getCodeChallengeMethod())
                .build()
                .toUri();
    }
    
    /**
     * Обменивает код авторизации на токены.
     *
     * @param code код авторизации из Keycloak
     * @param redirectUri URI перенаправления
     * @param codeVerifier код верификации из PKCE
     * @return объект TokenResponse с токенами
     * @throws RuntimeException если не удалось обменять код на токены
     */
    public TokenResponse exchangeCodeForTokens(String code, String redirectUri, String codeVerifier) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
            (clientId + ":" + clientSecret).getBytes()
        );
        headers.set("Authorization", authHeader);
        
        String body = String.format(
            "grant_type=authorization_code&code=%s&redirect_uri=%s&code_verifier=%s",
            code, redirectUri, codeVerifier
        );
        
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                tokenUri, request, TokenResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to exchange code for tokens", e);
        }
    }
    
    /**
     * Обновляет токены с помощью refresh токена.
     *
     * @param refreshToken refresh токен пользователя
     * @return объект TokenResponse с новыми токенами
     * @throws RuntimeException если не удалось обновить токены
     */
    public TokenResponse refreshTokens(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
            (clientId + ":" + clientSecret).getBytes()
        );
        headers.set("Authorization", authHeader);
        
        String body = String.format(
            "grant_type=refresh_token&refresh_token=%s",
            refreshToken
        );
        
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                tokenUri, request, TokenResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh tokens", e);
        }
    }
    
    /**
     * Генерирует URI для выхода пользователя из Keycloak.
     *
     * @param refreshToken refresh токен пользователя
     * @param redirectUri URI перенаправления после выхода
     * @return URI для выхода из Keycloak
     */
    public URI getLogoutUri(String refreshToken, String redirectUri) {
        return UriComponentsBuilder.fromUriString(logoutUri)
                .queryParam("client_id", clientId)
                .queryParam("refresh_token", refreshToken)
                .queryParam("post_logout_redirect_uri", redirectUri)
                .build()
                .toUri();
    }
}