package com.miltonbass.sgi_backend.sedes.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SedeDtos {

    // ── Crear sede ────────────────────────────────────────────────────
    public record CreateSedeRequest(
        @NotBlank @Size(min = 3, max = 20)
        @Pattern(regexp = "^[A-Z0-9_]+$",
                 message = "Solo mayúsculas, números y guión bajo")
        String codigo,

        @NotBlank @Size(min = 3, max = 100)
        String nombre,

        String descripcion,
        @Size(max = 100) String ciudad,
        @Size(max = 100) String departamento,
        @Size(max = 50)  String pais,
        String           direccion,
        @Size(max = 20)  String telefono,
        @Email @Size(max = 150) String email,
        @Size(max = 500) String logoUrl,
        @Size(max = 50)  String zonaHoraria,
        LocalDate        fechaFundacion,
        Map<String, Object> config
    ) {}

    // ── Actualizar sede ───────────────────────────────────────────────
    public record UpdateSedeRequest(
        @Size(min = 3, max = 100) String nombre,
        String descripcion,
        @Size(max = 100) String ciudad,
        @Size(max = 100) String departamento,
        @Size(max = 50)  String pais,
        String           direccion,
        @Size(max = 20)  String telefono,
        @Email @Size(max = 150) String email,
        @Size(max = 500) String logoUrl,
        @Size(max = 50)  String zonaHoraria,
        LocalDate        fechaFundacion,
        Map<String, Object> config
    ) {}

    // ── Respuesta ─────────────────────────────────────────────────────
    public record SedeResponse(
        UUID   id,
        String codigo,
        String nombre,
        String schemaName,
        String descripcion,
        String ciudad,
        String departamento,
        String pais,
        String direccion,
        String telefono,
        String email,
        String logoUrl,
        String zonaHoraria,
        Boolean activa,
        LocalDate fechaFundacion,
        Map<String, Object> config,
        Instant creadoEn,
        Instant actualizadoEn
    ) {}

    // ── Respuesta paginada ────────────────────────────────────────────
    public record SedePageResponse(
        List<SedeResponse> content,
        int  pageNumber,
        int  pageSize,
        long totalElements,
        int  totalPages
    ) {}
}
