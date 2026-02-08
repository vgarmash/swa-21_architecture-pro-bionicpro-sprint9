/**
 * Утилитный класс для работы с PKCE (Proof Key for Code Exchange).
 * Реализует генерацию кода верификации и кода вызова для OAuth2 PKCE.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Утилитный класс для генерации и обработки параметров PKCE.
 * PKCE используется для безопасности OAuth2 потока авторизации.
 */
public class PKCEUtil {
    
    /**
     * Генерирует код верификации для PKCE.
     *
     * @return строку с кодом верификации
     */
    public static String generateCodeVerifier() {
        byte[] codeVerifier = new byte[32];
        UUID.randomUUID().toString().getBytes();
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Генерирует код вызова из кода верификации с использованием SHA-256.
     *
     * @param codeVerifier код верификации
     * @return строку с кодом вызова в URL-совместимом Base64 формате
     * @throws RuntimeException если SHA-256 не поддерживается
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}