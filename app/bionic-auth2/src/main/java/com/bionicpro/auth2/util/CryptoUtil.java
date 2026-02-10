/**
 * Утилита для шифрования и дешифрования данных.
 * Использует AES-256-GCM для безопасного хранения токенов.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth2.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Класс для шифрования и дешифрования данных.
 * Используется для безопасного хранения токенов в Redis.
 */
public class CryptoUtil {
    
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    /**
     * Генерирует секретный ключ для AES-256.
     *
     * @return секретный ключ
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE, new SecureRandom());
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка генерации ключа", e);
        }
    }
    
    /**
     * Шифрует данные с использованием AES-256-GCM.
     *
     * @param data данные для шифрования
     * @param key секретный ключ
     * @return зашифрованные данные в формате base64 (IV + ciphertext)
     */
    public static String encrypt(String data, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
            
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка шифрования данных", e);
        }
    }
    
    /**
     * Дешифрует данные с использованием AES-256-GCM.
     *
     * @param encryptedData зашифрованные данные в формате base64
     * @param key секретный ключ
     * @return дешифрованные данные
     */
    public static String decrypt(String encryptedData, SecretKey key) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);
            
            if (combined.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Недостаточно данных для дешифрования");
            }
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка дешифрования данных", e);
        }
    }
}