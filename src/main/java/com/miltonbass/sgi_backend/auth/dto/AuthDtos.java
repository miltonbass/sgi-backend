package com.miltonbass.sgi_backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs del módulo de autenticación.
 * Se agrupan como records en un único archivo para mantener el código compacto.
 */
public final class AuthDtos {

    private AuthDtos() {}

    // ─── Requests ──────────────────────────────────────────────────────────────

    /**
     * Body del POST /api/auth/login
     * sedeId es opcional: si el usuario tiene una sola sede se usa automáticamente.
     */
    public record LoginRequest(
            @NotBlank @Email
            String email,

            @NotBlank @Size(min = 6, max = 128)
            String password,

            /** UUID de la sede deseada (opcional si el usuario solo tiene una) */
            String sedeId
    ) {}

    /**
     * Body del POST /api/auth/refresh
     */
    public record RefreshRequest(
            @NotBlank
            String refreshToken
    ) {}

    /**
     * Body del POST /api/auth/logout
     */
    public record LogoutRequest(
            @NotBlank
            String refreshToken
    ) {}

    // ─── Responses ─────────────────────────────────────────────────────────────

    /**
     * Respuesta exitosa del login y del refresh.
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,          // segundos hasta que expira el access token
            String tokenType,        // siempre "Bearer"
            UsuarioInfo usuario
    ) {}

    /**
     * Información del usuario incluida en la respuesta del login.
     */
    public record UsuarioInfo(
            String id,
            String email,
            String sedeActiva,       // código de la sede (ej: "PAI_BOG")
            String sedeSchema,       // schema Postgres (ej: "sede_pai_bog")
            java.util.List<String> roles,
            java.util.List<SedeInfo> sedes  // todas las sedes del usuario
    ) {}

    /**
     * Resumen de una sede para el listado en el token response.
     */
    public record SedeInfo(
            String id,
            String codigo,
            String nombreCorto
    ) {}

    /**
     * Respuesta de error estandarizada para el módulo de auth.
     */
    public record ErrorResponse(
            int status,
            String error,
            String mensaje,
            long timestamp
    ) {
        public static ErrorResponse of(int status, String error, String mensaje) {
            return new ErrorResponse(status, error, mensaje, System.currentTimeMillis());
        }
    }
}