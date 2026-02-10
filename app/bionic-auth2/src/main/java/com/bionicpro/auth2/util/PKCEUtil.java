/**
 * Утилита для генерации параметров PKCE (Proof Key for Code Exchange).
 * Используется для безопасности OAuth2 потока авторизации.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth2.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Класс для генерации параметров PKCE.
 * Используется для защиты OAuth2 потока авторизации от атаки с перехватом кода авторизации.
 */
public class PKCEUtil {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_VERIFIER_LENGTH = 43;
    
    /**
     * Генерирует случайный код верификации (code verifier) для PKCE.
     *
     * @return код верификации в формате base64url
     */
    public static String generateCodeVerifier() {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
    /**
     * Генерирует код вызова (code challenge) из кода верификации.
     * Использует SHA-256 хэширование.
     *
     * @param codeVerifier код верификации
     * @return код вызова в формате base64url
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка генерации кода вызова", e);
        }
    }
}