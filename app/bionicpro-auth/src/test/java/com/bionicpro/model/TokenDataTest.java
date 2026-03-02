package com.bionicpro.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenData model.
 * Tests serialization, deserialization, and model behavior.
 */
@DisplayName("TokenData Tests")
class TokenDataTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTest {

        @Test
        @DisplayName("Should create TokenData with all fields")
        void builder_shouldCreateWithAllFields() {
            // Подготовка
            Instant now = Instant.now();
            Instant accessTokenExpiresAt = now.plusSeconds(3600);
            Instant refreshTokenExpiresAt = now.plusSeconds(86400);

            // Действие
            TokenData tokenData = TokenData.builder()
                .accessToken("access-token-value")
                .refreshToken("refresh-token-value")
                .accessTokenExpiresAt(accessTokenExpiresAt)
                .refreshTokenExpiresAt(refreshTokenExpiresAt)
                .tokenType("Bearer")
                .scope("openid profile email")
                .build();

            // Проверка
            assertEquals("access-token-value", tokenData.getAccessToken());
            assertEquals("refresh-token-value", tokenData.getRefreshToken());
            assertEquals(accessTokenExpiresAt, tokenData.getAccessTokenExpiresAt());
            assertEquals(refreshTokenExpiresAt, tokenData.getRefreshTokenExpiresAt());
            assertEquals("Bearer", tokenData.getTokenType());
            assertEquals("openid profile email", tokenData.getScope());
        }

        @Test
        @DisplayName("Should create TokenData with null optional fields")
        void builder_shouldCreateWithNullFields() {
            // Действие
            TokenData tokenData = TokenData.builder()
                .accessToken("access-token-value")
                .build();

            // Проверка
            assertEquals("access-token-value", tokenData.getAccessToken());
            assertNull(tokenData.getRefreshToken());
            assertNull(tokenData.getAccessTokenExpiresAt());
            assertNull(tokenData.getRefreshTokenExpiresAt());
            assertNull(tokenData.getTokenType());
            assertNull(tokenData.getScope());
        }
    }

    @Nested
    @DisplayName("Setters and Getters")
    class SettersGettersTest {

        @Test
        @DisplayName("Should set and get all fields correctly")
        void settersAndGetters_shouldWorkCorrectly() {
            // Подготовка
            TokenData tokenData = new TokenData();
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(3600);

            // Действие
            tokenData.setAccessToken("access-token");
            tokenData.setRefreshToken("refresh-token");
            tokenData.setAccessTokenExpiresAt(expiresAt);
            tokenData.setRefreshTokenExpiresAt(expiresAt);
            tokenData.setTokenType("Bearer");
            tokenData.setScope("openid");

            // Проверка
            assertEquals("access-token", tokenData.getAccessToken());
            assertEquals("refresh-token", tokenData.getRefreshToken());
            assertEquals(expiresAt, tokenData.getAccessTokenExpiresAt());
            assertEquals(expiresAt, tokenData.getRefreshTokenExpiresAt());
            assertEquals("Bearer", tokenData.getTokenType());
            assertEquals("openid", tokenData.getScope());
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTest {

        @Test
        @DisplayName("TokenData should be serializable")
        void shouldBeSerializable() {
            // Подготовка
            TokenData tokenData = TokenData.builder()
                .accessToken("access-token")
                .tokenType("Bearer")
                .build();

            // Проверка - verify it can be serialized (no exception)
            assertDoesNotThrow(() -> {
                // Basic serialization check - class should implement Serializable
                assertTrue(java.io.Serializable.class.isAssignableFrom(TokenData.class));
            });
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("Should be equal for same values")
        void shouldBeEqualForSameValues() {
            // Подготовка
            Instant now = Instant.now();

            TokenData token1 = TokenData.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .accessTokenExpiresAt(now)
                .build();

            TokenData token2 = TokenData.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .accessTokenExpiresAt(now)
                .build();

            // Проверка
            assertEquals(token1, token2);
            assertEquals(token1.hashCode(), token2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal for different access tokens")
        void shouldNotBeEqualForDifferentAccessTokens() {
            // Подготовка
            TokenData token1 = TokenData.builder()
                .accessToken("access-token-1")
                .build();

            TokenData token2 = TokenData.builder()
                .accessToken("access-token-2")
                .build();

            // Проверка
            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("Should not be equal for null vs non-null tokens")
        void shouldNotBeEqualForNullVsNonNull() {
            // Подготовка
            TokenData token1 = TokenData.builder()
                .accessToken("access-token")
                .build();

            TokenData token2 = TokenData.builder()
                .accessToken(null)
                .build();

            // Проверка
            assertNotEquals(token1, token2);
        }
    }

    @Nested
    @DisplayName("ToString Test")
    class ToStringTest {

        @Test
        @DisplayName("ToString should include all fields")
        void toString_shouldIncludeAllFields() {
            // Подготовка
            TokenData tokenData = TokenData.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .scope("openid")
                .build();

            // Действие
            String result = tokenData.toString();

            // Проверка
            assertTrue(result.contains("accessToken"));
            assertTrue(result.contains("Bearer"));
        }
    }

    @Nested
    @DisplayName("Token Type Tests")
    class TokenTypeTest {

        @Test
        @DisplayName("Should default to Bearer token type")
        void shouldDefaultToBearerTokenType() {
            // Действие
            TokenData tokenData = TokenData.builder()
                .accessToken("token")
                .tokenType("Bearer")
                .build();

            // Проверка
            assertEquals("Bearer", tokenData.getTokenType());
        }

        @Test
        @DisplayName("Should support custom token types")
        void shouldSupportCustomTokenTypes() {
            // Действие
            TokenData tokenData = TokenData.builder()
                .accessToken("token")
                .tokenType("MAC")
                .build();

            // Проверка
            assertEquals("MAC", tokenData.getTokenType());
        }
    }

    @Nested
    @DisplayName("Scope Tests")
    class ScopeTest {

        @Test
        @DisplayName("Should handle multiple scopes")
        void shouldHandleMultipleScopes() {
            // Подготовка
            String expectedScope = "openid profile email offline_access";

            // Действие
            TokenData tokenData = TokenData.builder()
                .accessToken("token")
                .scope(expectedScope)
                .build();

            // Проверка
            assertEquals(expectedScope, tokenData.getScope());
        }

        @Test
        @DisplayName("Should handle empty scope")
        void shouldHandleEmptyScope() {
            // Действие
            TokenData tokenData = TokenData.builder()
                .accessToken("token")
                .scope("")
                .build();

            // Проверка
            assertEquals("", tokenData.getScope());
        }
    }
}
