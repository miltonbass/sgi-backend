package com.miltonbass.sgi_backend.auth.controller;

import com.miltonbass.sgi_backend.auth.dto.AuthDtos.*;
import com.miltonbass.sgi_backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST de autenticación.
 *
 * Endpoints:
 *   POST /api/auth/login    → Autenticar y obtener tokens
 *   POST /api/auth/refresh  → Renovar access token con refresh token
 *   POST /api/auth/logout   → Invalidar refresh token
 *
 * Todos son públicos (configurados en SecurityConfig).
 * El logout no requiere JWT válido — solo el refresh token a invalidar.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login
     *
     * Body:  { "email": "...", "password": "...", "sedeId": "UUID-opcional" }
     * 200:   { "accessToken": "...", "refreshToken": "...", "expiresIn": 3600, "usuario": {...} }
     * 401:   Credenciales inválidas o cuenta bloqueada
     * 400:   sedeId requerido para usuarios multi-sede
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String dispositivo = obtenerDispositivo(httpRequest);
        TokenResponse response = authService.login(request, dispositivo);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/refresh
     *
     * Body:  { "refreshToken": "..." }
     * 200:   Nuevo TokenResponse con access + refresh tokens
     * 401:   Refresh token inválido, expirado o ya utilizado
     *
     * Implementa rotation: el refresh token usado se invalida y se emite uno nuevo.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        String dispositivo = obtenerDispositivo(httpRequest);
        TokenResponse response = authService.refresh(request, dispositivo);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/logout
     *
     * Body:  { "refreshToken": "..." }
     * 204:   Logout exitoso (sin body)
     *
     * Revoca únicamente el refresh token del dispositivo actual.
     * Para revocar todos los dispositivos, el usuario puede usar
     * un endpoint futuro /api/auth/logout-all (Historia siguiente).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/auth/sedes?email=usuario@email.com
     *
     * Retorna las sedes activas del usuario para mostrar el selector antes del login.
     * Público — no requiere token. Devuelve [] si el email no existe.
     */
    @GetMapping("/sedes")
    public ResponseEntity<List<SedeInfo>> obtenerSedes(@RequestParam String email) {
        return ResponseEntity.ok(authService.obtenerSedesPorEmail(email));
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    /**
     * Construye una cadena de identificación del dispositivo para auditoría.
     * Formato: "IP | UserAgent"
     */
    private String obtenerDispositivo(HttpServletRequest request) {
        String ip = obtenerIpReal(request);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 200) {
            userAgent = userAgent.substring(0, 200);
        }
        return ip + " | " + (userAgent != null ? userAgent : "unknown");
    }

    /**
     * Obtiene la IP real considerando proxies inversos (NGINX).
     */
    private String obtenerIpReal(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For puede contener múltiples IPs: "client, proxy1, proxy2"
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}