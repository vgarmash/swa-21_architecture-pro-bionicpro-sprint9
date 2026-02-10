/**
 * Модель параметров PKCE (Proof Key for Code Exchange).
 * Хранит код верификации и код вызова для OAuth2 PKCE.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth2.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для хранения параметров PKCE.
 * Используется для безопасности OAuth2 потока авторизации.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PKCEParams {
    /**
     * Код верификации для PKCE
     */
    private String codeVerifier;
    
    /**
     * Код вызова для PKCE
     */
    private String codeChallenge;
    
    /**
     * Метод кода вызова (обычно "S256")
     */
    private String codeChallengeMethod = "S256";
}