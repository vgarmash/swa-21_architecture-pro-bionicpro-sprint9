package com.bionicpro.auth.controller;

import com.bionicpro.auth.model.request.LoginRequest;
import com.bionicpro.auth.model.request.RefreshRequest;
import com.bionicpro.auth.model.response.AuthResponse;
import com.bionicpro.auth.model.response.TokenResponse;
import com.bionicpro.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<String> login() {
        return ResponseEntity.ok(authService.initiateLogin());
    }

    @PostMapping("/callback")
    public ResponseEntity<AuthResponse> handleCallback(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.handleCallback(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@Valid @RequestBody RefreshRequest refreshRequest) {
        TokenResponse response = authService.refreshTokens(refreshRequest.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.noContent().build();
    }
}