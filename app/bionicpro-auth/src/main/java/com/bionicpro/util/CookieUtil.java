package com.bionicpro.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class CookieUtil {

    @Value("${auth.session.cookie-name:BIONICPRO_SESSION}")
    private String cookieName;

    @Value("${auth.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    /**
     * Извлекает значение сессионной куки из запроса
     *
     * @param request HttpServletRequest
     * @return Optional со значением куки или Optional.empty(), если кука не найдена
     */
    public Optional<String> getSessionCookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    /**
     * Устанавливает сессионную куку в ответ
     *
     * @param response HttpServletResponse
     * @param sessionValue значение сессии
     */
    public void setSessionCookie(HttpServletResponse response, String sessionValue) {
        Cookie cookie = new Cookie(cookieName, sessionValue);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite("Lax");
        cookie.setPath("/");
        cookie.setMaxAge(sessionTimeoutMinutes * 60); // в секундах
        response.addCookie(cookie);
    }

    /**
     * Удаляет сессионную куку
     *
     * @param response HttpServletResponse
     */
    public void removeSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite("Lax");
        cookie.setPath("/");
        cookie.setMaxAge(0); // Установка 0 удаляет куку
        response.addCookie(cookie);
    }
}