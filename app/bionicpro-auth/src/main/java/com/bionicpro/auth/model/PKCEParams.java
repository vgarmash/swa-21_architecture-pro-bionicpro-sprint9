/**
 * Модель для хранения параметров PKCE (Proof Key for Code Exchange).
 * Используется для безопасной аутентификации OAuth2 с использованием PKCE.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для хранения параметров PKCE.
 * PKCE - это механизм безопасности для OAuth2, который помогает предотвратить
 * атаки с подделкой кода (authorization code interception attacks).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PKCEParams {
    /**
     * Код верификации, используемый для генерации кода вызова
     */
    private String codeVerifier;
    
    /**
     * Код вызова, вычисленный из кода верификации с использованием SHA-256
     */
    private String codeChallenge;
    
    /**
     * Метод вызова кода (по умолчанию "S256")
     */
    private String codeChallengeMethod = "S256";
}