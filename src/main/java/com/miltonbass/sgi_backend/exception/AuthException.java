package com.miltonbass.sgi_backend.exception;

import java.time.Instant;

/**
 * Excepciones de dominio del módulo de autenticación.
 * Cada factory method devuelve un mensaje claro sin revelar detalles de seguridad.
 */
public class AuthException extends RuntimeException {

    private final int httpStatus;
    private final String codigo;

    private AuthException(int httpStatus, String codigo, String mensaje) {
        super(mensaje);
        this.httpStatus = httpStatus;
        this.codigo = codigo;
    }

    // ─── Factory methods ─────────────────────────────────────────────────────

    /** 401 - Credenciales incorrectas (mensaje genérico para no revelar si el email existe) */
    public static AuthException credencialesInvalidas() {
        return new AuthException(401, "CREDENCIALES_INVALIDAS", "Email o contraseña incorrectos");
    }

    /** 401 - Cuenta bloqueada temporalmente */
    public static AuthException cuentaBloqueada(Instant hastaInstant) {
        String hasta = hastaInstant != null ? hastaInstant.toString() : "unos minutos";
        return new AuthException(401, "CUENTA_BLOQUEADA",
                "Cuenta bloqueada temporalmente hasta " + hasta);
    }

    /** 401 - Cuenta desactivada */
    public static AuthException cuentaInactiva() {
        return new AuthException(401, "CUENTA_INACTIVA", "La cuenta está desactivada");
    }

    /** 401 - Refresh token inválido, expirado o ya usado */
    public static AuthException refreshTokenInvalido() {
        return new AuthException(401, "REFRESH_TOKEN_INVALIDO", "Token de actualización inválido o expirado");
    }

    /** 403 - Usuario sin sedes asignadas */
    public static AuthException sinSedesAsignadas() {
        return new AuthException(403, "SIN_SEDES", "El usuario no tiene sedes asignadas");
    }

    /** 403 - Sede no pertenece al usuario */
    public static AuthException sedeNoAsignada() {
        return new AuthException(403, "SEDE_NO_ASIGNADA", "No tienes acceso a la sede indicada");
    }

    /** 400 - Usuario multi-sede sin especificar cuál */
    public static AuthException multisedeSinSeleccion() {
        return new AuthException(400, "SEDE_REQUERIDA",
                "Tienes acceso a múltiples sedes. Especifica el campo sedeId en el request");
    }

    /** 503 - Sede no disponible (inactiva o eliminada) */
    public static AuthException sedeNoDisponible() {
        return new AuthException(503, "SEDE_NO_DISPONIBLE", "La sede no está disponible en este momento");
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public int getHttpStatus() { return httpStatus; }
    public String getCodigo() { return codigo; }
}