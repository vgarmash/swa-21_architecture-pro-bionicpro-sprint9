package com.bionicpro.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Утилитный класс для извлечения IP-адреса клиента из HTTP-запросов.
 * Обрабатывает различные заголовки прокси и проверяет IP-адреса, чтобы избежать приватных адресов по возможности.
 */
public final class ClientIpResolver {

    private static final Logger logger = LoggerFactory.getLogger(ClientIpResolver.class);

    /**
     * Имя заголовка для перенаправленных IP-адресов (может содержать несколько IP).
     */
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * Имя заголовка для реального IP-адреса клиента (устанавливается прокси типа nginx).
     */
    private static final String X_REAL_IP = "X-Real-IP";

    /**
     * Диапазоны приватных IP-адресов, которые должны быть проверены.
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
     * Приватный конструктор для предотвращения создания экземпляров.
     */
    private ClientIpResolver() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Извлекает IP-адрес клиента из HTTP-запроса.
     * <p>
     * Порядок разрешения:
     * 1. Заголовок X-Forwarded-For (первый IP - оригинальный клиент)
     * 2. Заголовок X-Real-IP
     * 3. request.getRemoteAddr()
     *
     * @param request HTTP-сервлет запроса
     * @return IP-адрес клиента, или null, если его невозможно определить
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String clientIp = null;

        // Сначала пробуем заголовок X-Forwarded-For (наиболее распространённый в продакшене)
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For может содержать несколько IP: "client, proxy1, proxy2"
            // Первый из них - оригинальный IP клиента
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                String firstIp = ips[0].trim();
                if (isValidIp(firstIp)) {
                    clientIp = firstIp;
                }
            }
        }

        // Пробуем заголовок X-Real-IP, если нет валидного IP из X-Forwarded-For
        if (clientIp == null) {
            String xRealIp = request.getHeader(X_REAL_IP);
            if (xRealIp != null && !xRealIp.isBlank()) {
                String trimmedIp = xRealIp.trim();
                if (isValidIp(trimmedIp)) {
                    clientIp = trimmedIp;
                }
            }
        }

        // Используем удалённый адрес как запасной вариант
        if (clientIp == null) {
            clientIp = request.getRemoteAddr();
        }

        logger.debug("Resolved client IP: {} from request URI: {}", clientIp, request.getRequestURI());
        return clientIp;
    }

    /**
     * Проверяет, является ли данный IP-адрес валидным.
     * Проверяет на null/пустоту и пытается проверить, что это правильный формат IP.
     *
     * @param ip IP-адрес для проверки
     * @return true, если IP является валидным, иначе false
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // Базовая проверка формата - проверка на допустимые символы
        if (!ip.matches("^[a-zA-Z0-9.:\\[\\]]+$")) {
            return false;
        }

        try {
            // Пытаемся распарсить как InetAddress для проверки формата
            InetAddress address = InetAddress.getByName(ip);
            
            // Проверяем, является ли это валидным юникаст-адресом (не null, не any local)
            return !address.isAnyLocalAddress() && !address.isMulticastAddress();
        } catch (UnknownHostException e) {
            logger.debug("Invalid IP address format: {}", ip);
            return false;
        }
    }

    /**
     * Проверяет, является ли данный IP-адрес приватным/локальным IP-адресом.
     *
     * @param ip IP-адрес для проверки
     * @return true, если IP является приватным/локальным, иначе false
     */
    public static boolean isPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // Проверяем по шаблонам приватных IP
        for (String pattern : PRIVATE_IP_PATTERNS) {
            if (ip.matches(pattern)) {
                return true;
            }
        }

        // Также используем InetAddress для дополнительной проверки
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Извлекает строку user agent из HTTP-запроса.
     *
     * @param request HTTP-сервлет запроса
     * @return строка user agent, или null, если недоступна
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader("User-Agent");
    }

    /**
     * Получает очищенную версию user agent для логирования.
     * Удаляет потенциально конфиденциальную информацию.
     *
     * @param userAgent сырая строка user agent
     * @return очищенный user agent или "Unknown", если null
     */
    public static String sanitizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        
        // Обрезаем, если слишком длинный
        if (userAgent.length() > 500) {
            return userAgent.substring(0, 500) + "...[truncated]";
        }
        
        return userAgent;
    }
}
