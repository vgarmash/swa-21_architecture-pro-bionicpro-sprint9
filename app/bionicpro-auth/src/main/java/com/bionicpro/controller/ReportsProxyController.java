package com.bionicpro.controller;

import com.bionicpro.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Proxy controller that forwards requests to the reports service.
 * Requires valid authentication and transforms requests appropriately.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ReportsProxyController {

    private final SessionService sessionService;
    
    @Autowired
    @Qualifier("proxyRestTemplate")
    private RestTemplate proxyRestTemplate;

    @Value("${REPORTS_SERVICE_URL:http://localhost:8081}")
    private String reportsServiceUrl;

    @RequestMapping("/api/reports/**")
    public ResponseEntity<String> proxyReportsApi(HttpServletRequest request, HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        
        log.debug("Proxying request: {} with query string: {}", requestUri, queryString);

        // Verify the user is authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("Current authentication: {}", authentication != null ? authentication.getName() : "null");
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication.getName() == null || Objects.equals(authentication.getName(), "anonymousUser")) {
            log.warn("Request blocked: unauthenticated user attempted to access {}", requestUri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                .body("{\"error\":\"not_authenticated\",\"message\":\"User not authenticated\"}");
        }

        try {
            // Build target URL by appending path to reports service URL
            // Transform /api/reports to /api/v1/reports to match the reports service API
            String targetPath = requestUri.replace("/api/reports", "/api/v1/reports");
            String targetUrlString = reportsServiceUrl + targetPath;
            
            if (queryString != null && !queryString.isEmpty()) {
                targetUrlString += "?" + queryString;
            }

            java.net.URI targetUrl = new java.net.URI(targetUrlString);

            log.debug("Forwarding request to: {}", targetUrl);
            
            // Prepare headers to forward, but exclude some that should not be forwarded
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                
                // Skip hop-by-hop headers that shouldn't be forwarded
                if (!"host".equalsIgnoreCase(headerName) &&
                    !"connection".equalsIgnoreCase(headerName) && 
                    !"upgrade".equalsIgnoreCase(headerName) &&
                    !"keep-alive".equalsIgnoreCase(headerName) &&
                    !"content-length".equalsIgnoreCase(headerName) &&  // Let Spring calculate this
                    !"transfer-encoding".equalsIgnoreCase(headerName)) {  // Let Spring handle this
                    
                    String headerValue = request.getHeader(headerName);
                    headers.add(headerName, headerValue);
                }
            }
            
            // Get access token from current authentication to add as Bearer token to reports service
            String accessToken = extractAccessToken();
            if (accessToken != null && !accessToken.trim().isEmpty()) {
                headers.setBearerAuth(accessToken);
            }
            
            // Prepare body if this is a mutating request
            String body = null;
            if (("POST".equalsIgnoreCase(request.getMethod()) || 
                 "PUT".equalsIgnoreCase(request.getMethod()) || 
                 "PATCH".equalsIgnoreCase(request.getMethod()))) {
                body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            }
            
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            
            // Make actual call to reports service
            ResponseEntity<String> backendResponse = proxyRestTemplate.exchange(
                targetUrl,
                HttpMethod.valueOf(request.getMethod()),
                entity,
                String.class
            );
            
            log.debug("Backend response status: {}", backendResponse.getStatusCodeValue());
            
            return ResponseEntity.status(backendResponse.getStatusCode())
                .headers(backendResponse.getHeaders())
                .body(backendResponse.getBody());
                
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("Reports service returned an error status: {}", e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error proxying request to reports service: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"gateway_error\",\"message\":\"Error connecting to reports service\"}");
        }
    }
    
    /**
     * Extract the access token from the authentication object.
     */
    private String extractAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object credentials = authentication.getCredentials();
            if (credentials != null) {
                return credentials.toString();
            }
        }
        return null;
    }
}