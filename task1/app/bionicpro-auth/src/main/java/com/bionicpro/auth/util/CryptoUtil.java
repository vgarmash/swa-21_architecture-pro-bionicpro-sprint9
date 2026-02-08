/**
 * Утилитный класс для шифрования и дешифрования данных.
 * Реализует AES шифрование для безопасного хранения чувствительных данных.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Утилитный класс для выполнения операций шифрования и дешифрования.
 * Используется для безопасного хранения и передачи чувствительных данных,
 * таких как refresh токены.
 */
public class CryptoUtil {
    
    /**
     * Алгоритм шифрования
     */
    private static final String ALGORITHM = "AES";
    
    /**
     * Трансформация для шифрования
     */
    private static final String TRANSFORMATION = "AES";
    
    /**
     * Шифрует текст с использованием AES.
     *
     * @param plainText текст для шифрования
     * @param encryptionKey ключ шифрования
     * @return зашифрованный текст в Base64 формате
     * @throws RuntimeException если шифрование не удалось
     */
    public static String encrypt(String plainText, String encryptionKey) {
        try {
            SecretKey secretKey = generateKey(encryptionKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Расшифровывает текст с использованием AES.
     *
     * @param encryptedText зашифрованный текст в Base64 формате
     * @param encryptionKey ключ шифрования
     * @return расшифрованный текст
     * @throws RuntimeException если дешифрование не удалось
     */
    public static String decrypt(String encryptedText, String encryptionKey) {
        try {
            SecretKey secretKey = generateKey(encryptionKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    /**
     * Генерирует ключ шифрования из строки.
     *
     * @param secretKey строка ключа
     * @return секретный ключ
     */
    private static SecretKey generateKey(String secretKey) {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
    }
    
    /**
     * Генерирует новый ключ шифрования AES 256.
     *
     * @return строку с Base64 закодированным ключом
     * @throws RuntimeException если не удалось сгенерировать ключ
     */
    public static String generateEncryptionKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(256);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
}