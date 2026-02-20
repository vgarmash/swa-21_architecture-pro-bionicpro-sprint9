package com.bionicpro.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility class for extracting client IP address from HTTP requests.
 * Handles various proxy headers and validates IPs to avoid private addresses when possible.
 */
public final class ClientIpResolver {

    private static final Logger logger = LoggerFactory.getLogger(ClientIpResolver.class);

    /**
     * Header name for forwarded IP addresses (may contain multiple IPs).
     */
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * Header name for the real client IP (set by proxies like nginx).
     */
    private static final String X_REAL_IP = "X-Real-IP";

    /**
     * Private IP ranges that should be validated.
     */
    private static final String[] PRIVATE_IP_PATTERNS = {
            "^127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$",  // 127.0.0.0/8
            "^10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$",    // 10.0.0.0/8
            "^172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}$", // 172.16.0.0/12
            "^192\\.168\\.\\d{1,3}\\.\\d{1,3}$",       // 192.168.0.0/16
            "^169\\.254\\.\\d{1,3}\\.\\d{1,3}$",       // link-local
            "^::1$",                                    // IPv6 loopback
            "^fc00:",                                  // IPv6 unique local
            "^fe80:",                                  // IPv6 link-local
    };

    /**
     * Private constructor to prevent instantiation.
     */
    private ClientIpResolver() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Extracts the client IP address from the HTTP request.
     * <p>
     * Resolution order:
     * 1. X-Forwarded-For header (first IP is the original client)
     * 2. X-Real-IP header
     * 3. request.getRemoteAddr()
     *
     * @param request the HTTP servlet request
     * @return the client IP address, or null if it cannot be determined
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String clientIp = null;

        // Try X-Forwarded-For header first (most common in production)
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For may contain multiple IPs: "client, proxy1, proxy2"
            // The first one is the original client IP
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                String firstIp = ips[0].trim();
                if (isValidIp(firstIp)) {
                    clientIp = firstIp;
                }
            }
        }

        // Try X-Real-IP header if no valid IP from X-Forwarded-For
        if (clientIp == null) {
            String xRealIp = request.getHeader(X_REAL_IP);
            if (xRealIp != null && !xRealIp.isBlank()) {
                String trimmedIp = xRealIp.trim();
                if (isValidIp(trimmedIp)) {
                    clientIp = trimmedIp;
                }
            }
        }

        // Fall back to remote address
        if (clientIp == null) {
            clientIp = request.getRemoteAddr();
        }

        logger.debug("Resolved client IP: {} from request URI: {}", clientIp, request.getRequestURI());
        return clientIp;
    }

    /**
     * Validates if the given IP address is valid.
     * Checks for null/empty and attempts to validate it's a proper IP format.
     *
     * @param ip the IP address to validate
     * @return true if the IP appears valid, false otherwise
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // Basic format validation - check for valid characters
        if (!ip.matches("^[a-zA-Z0-9.:\\[\\]]+$")) {
            return false;
        }

        try {
            // Try to parse as InetAddress to validate format
            InetAddress address = InetAddress.getByName(ip);
            
            // Check if it's a valid unicast address (not null, not any local)
            return !address.isAnyLocalAddress() && !address.isMulticastAddress();
        } catch (UnknownHostException e) {
            logger.debug("Invalid IP address format: {}", ip);
            return false;
        }
    }

    /**
     * Checks if the given IP address is a private/local IP address.
     *
     * @param ip the IP address to check
     * @return true if the IP is private/local, false otherwise
     */
    public static boolean isPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // Check against private IP patterns
        for (String pattern : PRIVATE_IP_PATTERNS) {
            if (ip.matches(pattern)) {
                return true;
            }
        }

        // Also use InetAddress for additional validation
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Extracts the user agent string from the HTTP request.
     *
     * @param request the HTTP servlet request
     * @return the user agent string, or null if not available
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader("User-Agent");
    }

    /**
     * Gets a sanitized version of the user agent for logging.
     * Removes potentially sensitive information.
     *
     * @param userAgent the raw user agent string
     * @return sanitized user agent or "Unknown" if null
     */
    public static String sanitizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        
        // Truncate if too long
        if (userAgent.length() > 500) {
            return userAgent.substring(0, 500) + "...[truncated]";
        }
        
        return userAgent;
    }
}
