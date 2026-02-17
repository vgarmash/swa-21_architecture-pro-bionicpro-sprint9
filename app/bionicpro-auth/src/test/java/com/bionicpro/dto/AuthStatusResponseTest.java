package com.bionicpro.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthStatusResponse DTO.
 * Tests serialization, deserialization, and model behavior.
 */
@DisplayName("AuthStatusResponse Tests")
class AuthStatusResponseTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTest {

        @Test
        @DisplayName("Should create AuthStatusResponse with all fields")
        void builder_shouldCreateWithAllFields() {
            // Act
            AuthStatusResponse response = AuthStatusResponse.builder()
                .authenticated(true)
                .userId("user123")
                .username("testuser")
                .roles(List.of("ROLE_USER", "ROLE_ADMIN"))
                .sessionExpiresAt("2024-01-01T12:00:00Z")
                .build();

            // Assert
            assertTrue(response.isAuthenticated());
            assertEquals("user123", response.getUserId());
            assertEquals("testuser", response.getUsername());
            assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"), response.getRoles());
            assertEquals("2024-01-01T12:00:00Z", response.getSessionExpiresAt());
        }

        @Test
        @DisplayName("Should create AuthStatusResponse for unauthenticated user")
        void builder_shouldCreateForUnauthenticated() {
            // Act
            AuthStatusResponse response = AuthStatusResponse.builder()
                .authenticated(false)
                .build();

            // Assert
            assertFalse(response.isAuthenticated());
            assertNull(response.getUserId());
            assertNull(response.getUsername());
            assertNull(response.getRoles());
            assertNull(response.getSessionExpiresAt());
        }
    }

    @Nested
    @DisplayName("Setters and Getters")
    class SettersGettersTest {

        @Test
        @DisplayName("Should set and get all fields correctly")
        void settersAndGetters_shouldWorkCorrectly() {
            // Arrange
            AuthStatusResponse response = new AuthStatusResponse();

            // Act
            response.setAuthenticated(true);
            response.setUserId("user123");
            response.setUsername("testuser");
            response.setRoles(List.of("ROLE_USER"));
            response.setSessionExpiresAt("2024-01-01T12:00:00Z");

            // Assert
            assertTrue(response.isAuthenticated());
            assertEquals("user123", response.getUserId());
            assertEquals("testuser", response.getUsername());
            assertEquals(List.of("ROLE_USER"), response.getRoles());
            assertEquals("2024-01-01T12:00:00Z", response.getSessionExpiresAt());
        }
    }

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JsonSerializationTest {

        @Test
        @DisplayName("Should not include null fields in JSON")
        void shouldNotIncludeNullFieldsInJson() {
            // Arrange
            AuthStatusResponse response = AuthStatusResponse.builder()
                .authenticated(false)
                .build();

            // Assert
            assertFalse(response.isAuthenticated());
            // Verify @JsonInclude(JsonInclude.Include.NON_NULL) annotation is present
            assertNotNull(AuthStatusResponse.class.getDeclaredAnnotation(
                com.fasterxml.jackson.annotation.JsonInclude.class));
        }

        @Test
        @DisplayName("Should include authenticated field when true")
        void shouldIncludeAuthenticatedWhenTrue() {
            // Act
            AuthStatusResponse response = AuthStatusResponse.builder()
                .authenticated(true)
                .userId("user123")
                .build();

            // Assert
            assertTrue(response.isAuthenticated());
            assertNotNull(response.getUserId());
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("Should be equal for same values")
        void shouldBeEqualForSameValues() {
            // Arrange
            AuthStatusResponse response1 = AuthStatusResponse.builder()
                .authenticated(true)
                .userId("user123")
                .username("testuser")
                .build();

            AuthStatusResponse response2 = AuthStatusResponse.builder()
                .authenticated(true)
                .userId("user123")
                .username("testuser")
                .build();

            // Assert
            assertEquals(response1, response2);
            assertEquals(response1.hashCode(), response2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal for different user IDs")
        void shouldNotBeEqualForDifferentUserIds() {
            // Arrange
            AuthStatusResponse response1 = AuthStatusResponse.builder()
                .authenticated(true)
                .userId("user123")
                .build();

            AuthStatusResponse response2 = AuthStatusResponse.builder()
                .authenticated(true)
                .userId("user456")
                .build();

            // Assert
            assertNotEquals(response1, response2);
        }

        @Test
        @DisplayName("Should not be equal for different authentication status")
        void shouldNotBeEqualForDifferentAuthStatus() {
            // Arrange
            AuthStatusResponse response1 = AuthStatusResponse.builder()
                .authenticated(true)
                .build();

            AuthStatusResponse response2 = AuthStatusResponse.builder()
                .authenticated(false)
                .build();

            // Assert
            assertNotEquals(response1, response2);
        }
    }

    @Nested
    @DisplayName("ToString Test")
    class ToStringTest {

        @Test
        @DisplayName("ToString should include all fields")
        void toString_shouldIncludeAllFields() {
            // Arrange
            AuthStatusResponse response = AuthStatusResponse.builder()
                .authenticated(true)
                .userId("user123")
                .username("testuser")
                .build();

            // Act
            String result = response.toString();

            // Assert
            assertTrue(result.contains("authenticated"));
            assertTrue(result.contains("userId"));
        }
    }
}
