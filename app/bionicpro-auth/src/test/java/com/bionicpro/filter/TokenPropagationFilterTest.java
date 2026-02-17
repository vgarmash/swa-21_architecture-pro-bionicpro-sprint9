package com.bionicpro.filter;

import com.bionicpro.model.SessionData;
import com.bionicpro.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for TokenPropagationFilter.
 * Tests token extraction, validation and propagation to backend requests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenPropagationFilter Tests")
class TokenPropagationFilterTest {

    @Mock
    private SessionService sessionService;

    private TokenPropagationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TokenPropagationFilter(sessionService);
    }

    @Nested
    @DisplayName("Filter Chain Processing")
    class FilterChainProcessingTest {

        @Test
        @DisplayName("Should skip filter for auth endpoints")
        void doFilterInternal_shouldSkipAuthEndpoints() throws Exception {
            // Arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/auth/login");
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            
            // Act
            filter.doFilterInternal(request, response, filterChain);
            
            // Assert
            verify(filterChain).doFilter(request, response);
            verify(sessionService, never()).validateAndRefreshSession(anyString());
        }

        @Test
        @DisplayName("Should return 401 when no session cookie")
        void doFilterInternal_shouldReturn401WhenNoCookie() throws Exception {
            // Arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/users");
            request.setCookies();
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            
            // Act
            filter.doFilterInternal(request, response, filterChain);
            
            // Assert
            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("not_authenticated"));
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("Should return 401 when session is invalid")
        void doFilterInternal_shouldReturn401WhenSessionInvalid() throws Exception {
            // Arrange
            Cookie sessionCookie = new Cookie("BIONICPRO_SESSION", "invalid-session");
            
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/users");
            request.setCookies(sessionCookie);
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            
            when(sessionService.validateAndRefreshSession("invalid-session")).thenReturn(null);
            
            // Act
            filter.doFilterInternal(request, response, filterChain);
            
            // Assert
            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("Session expired or invalid"));
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("Should return 401 when access token is missing")
        void doFilterInternal_shouldReturn401WhenAccessTokenMissing() throws Exception {
            // Arrange
            Cookie sessionCookie = new Cookie("BIONICPRO_SESSION", "valid-session");
            
            SessionData sessionData = SessionData.builder()
                .sessionId("valid-session")
                .userId("user123")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
            
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/users");
            request.setCookies(sessionCookie);
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            
            when(sessionService.validateAndRefreshSession("valid-session")).thenReturn(sessionData);
            when(sessionService.getAccessToken("valid-session")).thenReturn(null);
            
            // Act
            filter.doFilterInternal(request, response, filterChain);
            
            // Assert
            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("No access token"));
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("Should continue filter chain when session is valid")
        void doFilterInternal_shouldContinueChainWhenSessionValid() throws Exception {
            // Arrange
            Cookie sessionCookie = new Cookie("BIONICPRO_SESSION", "valid-session");
            
            SessionData sessionData = SessionData.builder()
                .sessionId("valid-session")
                .userId("user123")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
            
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/users");
            request.setCookies(sessionCookie);
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            
            when(sessionService.validateAndRefreshSession("valid-session")).thenReturn(sessionData);
            when(sessionService.getAccessToken("valid-session")).thenReturn("access-token-123");
            
            // Act
            filter.doFilterInternal(request, response, filterChain);
            
            // Assert
            assertEquals("access-token-123", request.getAttribute("accessToken"));
            assertEquals("user123", request.getAttribute("userId"));
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle empty cookies array")
        void doFilterInternal_shouldHandleEmptyCookies() throws Exception {
            // Arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/users");
            request.setCookies(new Cookie[]{});
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            
            // Act
            filter.doFilterInternal(request, response, filterChain);
            
            // Assert
            assertEquals(401, response.getStatus());
        }

        @Test
        @DisplayName("Should handle different cookie name")
        void doFilterInternal_shouldHandleDifferentCookieName() throws Exception {
            // Arrange
            Cookie otherCookie = new Cookie("OTHER_COOKIE", "some-value");
            
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/users");
            request.setCookies(otherCookie);
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            
            // Act
            filter.doFilterInternal(request, response, filterChain);
            
            // Assert
            assertEquals(401, response.getStatus());
        }

        @Test
        @DisplayName("Should handle null cookies from request")
        void doFilterInternal_shouldHandleNullCookies() throws Exception {
            // Arrange
            MockHttpServletRequest request = new MockHttpServletRequest() {
                @Override
                public Cookie[] getCookies() {
                    return null;
                }
            };
            request.setRequestURI("/api/users");
            
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            
            // Act
            filter.doFilterInternal(request, response, filterChain);
            
            // Assert
            assertEquals(401, response.getStatus());
        }
    }
}
