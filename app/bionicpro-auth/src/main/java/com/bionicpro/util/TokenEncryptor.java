package com.bionicpro.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenEncryptor {

    @Value("${auth.token.encryption.algorithm:AES}")
    private String algorithm = "AES";

    @Value("${auth.token.encryption.key-size:256}")
    private int keySize = 256;

    @Value("${auth.token.encryption.mode:CBC}")
    private String mode = "CBC";

    @Value("${auth.token.encryption.padding:PKCS5Padding}")
    private String padding = "PKCS5Padding";

    // В реальном приложении этот ключ должен быть загружен из безопасного источника
    // Для демонстрации используем генерацию ключа
    private SecretKey secretKey;

    public TokenEncryptor() {
        try {
            // Генерируем ключ для шифрования
            KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
            keyGenerator.init(keySize);
            this.secretKey = keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации ключа шифрования", e);
        }
    }

    /**
     * Шифрует строку с использованием AES
     *
     * @param plainText текст для шифрования
     * @return зашифрованная строка в Base64
     */
    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm + "/" + mode + "/" + padding);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            
            // Соединяем IV и зашифрованные данные
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при шифровании токена", e);
        }
    }

    /**
     * Расшифровывает строку с использованием AES
     *
     * @param encryptedText зашифрованная строка в Base64
     * @return расшифрованный текст
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            
            // Извлекаем IV (первые 16 байт для AES)
            byte[] iv = new byte[16];
            byte[] encrypted = new byte[combined.length - 16];
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encrypted, 0, encrypted.length);
            
            Cipher cipher = Cipher.getInstance(algorithm + "/" + mode + "/" + padding);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при расшифровке токена", e);
        }
    }

    /**
     * Генерирует случайный ключ шифрования (для демонстрации)
     * В реальном приложении этот метод не должен использоваться
     */
    public SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
            keyGenerator.init(keySize);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации ключа", e);
        }
    }

    /**
     * Устанавливает ключ шифрования (для демонстрации)
     * В реальном приложении этот метод не должен использоваться
     */
    public void setSecretKey(SecretKey key) {
        this.secretKey = key;
    }
}