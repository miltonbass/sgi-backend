package com.miltonbass.sgi_backend.celulas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SesionGrupoDtos {

    private SesionGrupoDtos() {}

    // ─── Requests ─────────────────────────────────────────────────────────────

    public record CreateSesionRequest(
            @NotNull LocalDate fecha,
            @Size(max = 200) String lugar,
            @Size(max = 200) String tema,
            String comentarios,
            BigDecimal ofrendaMonto) {}

    public record UpdateSesionRequest(
            LocalDate fecha,
            @Size(max = 200) String lugar,
            @Size(max = 200) String tema,
            String comentarios,
            BigDecimal ofrendaMonto) {}

    public record RegistrarAsistenciaSesionRequest(
            UUID miembroId,
            @Size(max = 150) String visitanteNombre,
            @Size(max = 20)  String visitanteTelefono) {}

    // ─── Responses ────────────────────────────────────────────────────────────

    public record SesionResponse(
            UUID id,
            UUID grupoId,
            String grupoNombre,
            LocalDate fecha,
            String lugar,
            String tema,
            String comentarios,
            BigDecimal ofrendaMonto,
            UUID creadoPor,
            Instant creadoEn,
            Instant actualizadoEn,
            int totalPresentes,
            int totalMiembros) {}

    public record AsistenciaSesionResponse(
            UUID id,
            UUID sesionId,
            UUID miembroId,
            String miembroNombres,
            String miembroApellidos,
            String visitanteNombre,
            String visitanteTelefono,
            boolean presente,
            Instant creadoEn) {}

    public record AsistenciaSesionListResponse(
            List<AsistenciaSesionResponse> asistencias,
            int totalPresentes,
            int totalMiembros,
            LocalDate sesionFecha,
            String grupoNombre) {}
}
