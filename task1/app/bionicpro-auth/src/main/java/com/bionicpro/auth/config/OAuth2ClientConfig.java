/**
 * Конфигурация OAuth2 клиента для работы с Keycloak.
 * Настройка авторизационного менеджера и репозитория авторизованных клиентов.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;

/**
 * Конфигурация OAuth2 клиента для работы с Keycloak.
 * Настройка менеджера авторизованных клиентов для обработки OAuth2 потоков.
 */
@Configuration
public class OAuth2ClientConfig {
    
    /**
     * Создает менеджер авторизованных клиентов OAuth2.
     * Настройка провайдеров для получения токенов через authorization code и refresh token.
     *
     * @param clientRegistrationRepository репозиторий регистрации клиентов
     * @param authorizedClientRepository репозиторий авторизованных клиентов
     * @return менеджер авторизованных клиентов OAuth2
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {
        
        OAuth2AuthorizedClientProvider authorizedClientProvider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build();
        
        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
            new DefaultOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientRepository);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        
        return authorizedClientManager;
    }
    
    /**
     * Создает репозиторий авторизованных клиентов OAuth2.
     * Используется для хранения информации о авторизованных клиентах в сессии.
     *
     * @return репозиторий авторизованных клиентов OAuth2
     */
    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }
}