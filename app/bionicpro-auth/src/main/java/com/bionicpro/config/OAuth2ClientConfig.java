package com.bionicpro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/**
 * Конфигурация OAuth2 Client для интеграции с Keycloak.
 * Настраивает Authorization Code Flow с PKCE.
 */
@Configuration
public class OAuth2ClientConfig {

    @Value("${keycloak.server-url:http://localhost:8080}")
    private String keycloakServerUrl;

    @Value("${keycloak.public-url:}")
    private String keycloakPublicUrl;

    @Value("${keycloak.realm:reports-realm}")
    private String realm;

    @Value("${keycloak.client-id:bionicpro-auth}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    @Value("${keycloak.redirect-uri:http://localhost:8000/api/auth/callback}")
    private String redirectUri;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                .authorizationUri((keycloakPublicUrl != null && !keycloakPublicUrl.isEmpty() ? keycloakPublicUrl : keycloakServerUrl) + "/realms/" + realm + "/protocol/openid-connect/auth")
                .tokenUri(keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .userInfoUri(keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo")
                .jwkSetUri(keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/certs")
                .issuerUri(keycloakServerUrl + "/realms/" + realm)
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Keycloak")
                .build();

        return new InMemoryClientRegistrationRepository(registration);
    }

    @Value("${oauth2.aes-key}")
    private String aesKey;

    @Value("${oauth2.salt}")
    private String salt;

    @Bean
    public BytesEncryptor bytesEncryptor() {
        // Используем постоянный ключ из переменной окружения
        return new AesBytesEncryptor(aesKey, salt);
    }
}
