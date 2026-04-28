// src/main/java/com/miltonbass/sgi_backend/miembros/dto/MiembroDtos.java
package com.miltonbass.sgi_backend.miembros.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MiembroDtos {

    private MiembroDtos() {
    }

    // ─── Requests ────────────────────────────────────────────

    public record CreateMiembroRequest(
            @NotBlank @Size(min = 2, max = 100) String nombres,

            @NotBlank @Size(min = 2, max = 100) String apellidos,

            /** Email único por sede — validado en servicio contra el tenant actual */
            @Email @Size(max = 150) String email,

            @Size(max = 20) String telefono,

            LocalDate fechaNacimiento,

            String direccion,

            @Size(max = 20) String estadoCivil, // SOLTERO, CASADO, VIUDO, DIVORCIADO, UNION_LIBRE

            // Campos opcionales
            @Size(max = 20) String cedula,
            @Size(max = 20) String numeroMiembro,
            @Size(max = 10) String genero, // M, F, OTRO
            @Size(max = 100) String ciudad,
            @Size(max = 500) String fotoUrl,
            LocalDate fechaIngreso, // si null → hoy (en @PrePersist)
            LocalDate fechaBautismo,
            UUID grupoId,
            Map<String, Object> metadata) {
    }

    public record UpdateMiembroRequest(
            @Size(min = 2, max = 100) String nombres,
            @Size(min = 2, max = 100) String apellidos,
            @Email @Size(max = 150) String email,
            @Size(max = 20) String telefono,
            LocalDate fechaNacimiento,
            String direccion,
            @Size(max = 20) String estadoCivil,
            @Size(max = 20) String cedula,
            @Size(max = 10) String genero,
            @Size(max = 100) String ciudad,
            @Size(max = 500) String fotoUrl,
            LocalDate fechaIngreso,
            LocalDate fechaBautismo,
            UUID grupoId,

            /**
             * Transiciones de estado permitidas:
             * VISITOR → MIEMBRO, INACTIVO
             * MIEMBRO → INACTIVO, RESTAURADO
             * INACTIVO → RESTAURADO
             * RESTAURADO → MIEMBRO, INACTIVO
             * Validadas en MiembroService.validarTransicionEstado()
             */
            @Pattern(regexp = "VISITOR|MIEMBRO|INACTIVO|RESTAURADO", message = "Estado debe ser VISITOR, MIEMBRO, INACTIVO o RESTAURADO") String estado,

            Map<String, Object> metadata) {
    }

    // ─── Responses ───────────────────────────────────────────

    public record MiembroResponse(
            UUID id,
            UUID sedeId,
            UUID creadoPor,
            String numeroMiembro,
            String cedula,
            String nombres,
            String apellidos,
            LocalDate fechaNacimiento,
            String genero,
            String estadoCivil,
            String telefono,
            String email,
            String direccion,
            String ciudad,
            String fotoUrl,
            String estado,
            LocalDate fechaIngreso,
            LocalDate fechaBautismo,
            UUID grupoId,
            Map<String, Object> metadata,
            Instant creadoEn,
            Instant actualizadoEn) {
    }

    public record MiembroPageResponse(
            List<MiembroResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages) {
    }
}