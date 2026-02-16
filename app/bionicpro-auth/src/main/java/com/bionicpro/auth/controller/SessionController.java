package com.bionicpro.auth.controller;

import com.bionicpro.auth.model.response.SessionInfo;
import com.bionicpro.auth.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/session")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    @GetMapping("/info")
    public ResponseEntity<SessionInfo> getSessionInfo() {
        // Имитируем получение информации о сессии
        SessionInfo sessionInfo = null;
        return ResponseEntity.ok(sessionInfo);
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateSession() {
        // Имитируем проверку валидности сессии
        Boolean isValid = false;
        return ResponseEntity.ok(isValid);
    }

    @GetMapping
    public ResponseEntity<List<SessionInfo>> getAllSessions() {
        List<SessionInfo> sessions = sessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionInfo> getSessionById(@PathVariable String sessionId) {
        SessionInfo session = sessionService.getSessionById(sessionId);
        return ResponseEntity.ok(session);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}