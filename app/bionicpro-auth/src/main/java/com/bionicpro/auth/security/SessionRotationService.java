package com.bionicpro.auth.security;

import com.bionicpro.auth.service.KeycloakService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SessionRotationService {

    private final KeycloakService keycloakService;
    private final TokenStore tokenStore;

    public SessionRotationService(KeycloakService keycloakService, TokenStore tokenStore) {
        this.keycloakService = keycloakService;
        this.tokenStore = tokenStore;
    }

    public void rotateSession(HttpServletRequest request, HttpServletResponse response) {
        // Получаем текущую аутентификацию
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

        if (currentAuth != null && currentAuth.isAuthenticated()) {
            String currentSessionId = request.getSession().getId();

            // Генерируем новый session ID
            HttpSession newSession = request.getSession(false);
            if (newSession != null) {
                newSession.invalidate();
            }

            HttpSession rotatedSession = request.getSession(true);
            String newSessionId = rotatedSession.getId();

            // Обновляем привязку токенов к новой сессии
            updateTokenSessionBinding(currentAuth, newSessionId);

            // Устанавливаем новый session ID в cookie
            setNewSessionCookie(response, newSessionId);

            // Добавляем новый session ID в ответ для фронтенда
            response.setHeader("X-New-Session-ID", newSessionId);
        }
    }

    private void updateTokenSessionBinding(Authentication auth, String newSessionId) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            // Сохраняем связь токена с новой сессией
            tokenStore.updateTokenSessionBinding(jwt.getTokenValue(), newSessionId);
        }
    }

    private void setNewSessionCookie(HttpServletResponse response, String sessionId) {
        Cookie sessionCookie = new Cookie("JSESSIONID", sessionId);
        sessionCookie.setPath("/");
        sessionCookie.setHttpOnly(true);
        sessionCookie.setSecure(true); // Для HTTPS
        sessionCookie.setMaxAge(-1); // Сессия браузера
        response.addCookie(sessionCookie);
    }
}

