package com.miltonbass.sgi_backend.usuarios.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class UsuarioDtos {

    private UsuarioDtos() {}

    // ─── Requests ───────────────────────────────────────────

    public record CreateUsuarioRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 2, max = 50)
        String nombre,

        @NotBlank @Size(min = 2, max = 50)
        String apellido,

        @Size(max = 50)
        String username,     // si null → se genera del email

        @Size(max = 20)
        String telefono,

        // Asignacion inicial opcional (evita un segundo request)
        UUID sedeId,
        List<String> roles
    ) {}

    public record AsignarSedeRequest(
        @NotNull
        UUID sedeId,

        @NotEmpty
        List<String> roles
    ) {}

    public record UpdateUsuarioRequest(
        @Size(min = 2, max = 50)
        String nombre,
        @Size(min = 2, max = 50)
        String apellido,
        @Size(max = 20)
        String telefono,
        Boolean activo
    ) {}

    // ─── Responses ──────────────────────────────────────────

    public record UsuarioResponse(
        String id,
        String email,
        String username,
        String nombre,
        String apellido,
        String telefono,
        boolean activo,
        boolean debeActivar,
        String ultimoLogin,
        List<SedeAsignada> sedes
    ) {}

    public record SedeAsignada(
        String sedeId,
        String sedeCodigo,
        String sedeNombre,
        List<String> roles
    ) {}

    public record UsuarioListResponse(
        List<UsuarioResponse> usuarios,
        int total,
        int pagina,
        int tamano
    ) {}

    public record ErrorResponse(int status, String error, String mensaje, long timestamp) {
        public static ErrorResponse of(int s, String e, String m) {
            return new ErrorResponse(s, e, m, System.currentTimeMillis());
        }
    }
}
