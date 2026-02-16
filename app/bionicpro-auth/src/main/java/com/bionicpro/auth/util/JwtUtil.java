package com.bionicpro.auth.util;

import com.bionicpro.auth.config.JwtConfig;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final String HMAC_SHA_512 = "HmacSHA512";
    
    private final JwtConfig jwtConfig;

    public JwtUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, HMAC_SHA_512);
    }

    public String generateAccessToken(String username) {
        long expiration = jwtConfig.getExpiration(); // в миллисекундах
        return generateJwtToken(username, expiration);
    }

    public String generateRefreshToken(String username) {
        long expiration = 7 * 24 * 60 * 60 * 1000; // 7 дней
        return generateJwtToken(username, expiration);
    }

    private String generateJwtToken(String username, long expirationMillis) {
        long nowMillis = System.currentTimeMillis();
        long expiryMillis = nowMillis + expirationMillis;

        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS512\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.format(
                        "{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}",
                        username, nowMillis / 1000, expiryMillis / 1000
                ).getBytes(StandardCharsets.UTF_8));

        String signature = generateSignature(header + "." + payload, getSigningKey());

        return header + "." + payload + "." + signature;
    }

    private String generateSignature(String input, SecretKey key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_512);
            mac.init(key);
            byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Error generating signature", e);
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, payload -> {
            // Extract "sub" field from JWT payload
            int firstQuote = payload.indexOf("\"sub\":\"") + 7;
            int secondQuote = payload.indexOf("\"", firstQuote);
            return payload.substring(firstQuote, secondQuote);
        });
    }

    public String getUsernameFromRefreshToken(String refreshToken) {
        return getUsernameFromToken(refreshToken);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, payload -> {
            // Extract "exp" field from JWT payload
            int expIndex = payload.indexOf("\"exp\":") + 6;
            int commaIndex = payload.indexOf(",", expIndex);
            if (commaIndex == -1) commaIndex = payload.indexOf("}", expIndex);
            String expStr = payload.substring(expIndex, commaIndex).trim();
            long expSeconds = Long.parseLong(expStr);
            return Date.from(Instant.ofEpochSecond(expSeconds));
        });
    }

    public <T> T getClaimFromToken(String token, Function<String, T> payloadResolver) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token");
        }
        
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return payloadResolver.apply(payload);
    }

    public boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    public boolean validateToken(String token, String username) {
        final String tokenUsername = getUsernameFromToken(token);
        return (tokenUsername.equals(username) && !isTokenExpired(token));
    }
}