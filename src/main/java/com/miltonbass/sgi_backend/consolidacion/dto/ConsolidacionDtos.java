package com.miltonbass.sgi_backend.consolidacion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ConsolidacionDtos {

    private ConsolidacionDtos() {}

    // ─── Requests ────────────────────────────────────────────────────────

    public record CompletarTareaRequest(String notas) {}

    public record ConfiguracionConsolidacionRequest(
            @NotNull @Min(1) @Max(100)
            Integer maxAsignadosConsolidador) {}

    // ─── Responses ───────────────────────────────────────────────────────

    public record ConsolidadorResponse(
            UUID id,
            String nombres,
            String apellidos,
            String telefono,
            int asignadosActivos,
            int maxAsignados) {}

    public record TareaConsolidacionResponse(
            UUID id,
            UUID miembroId,
            String miembroNombres,
            String miembroApellidos,
            String miembroTelefono,
            UUID consolidadorId,
            String consolidadorNombres,
            String consolidadorApellidos,
            String tipo,
            String estado,
            String descripcion,
            String notas,
            Instant creadoEn,
            Instant completadaEn) {}

    public record TareaConsolidacionPageResponse(
            List<TareaConsolidacionResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages) {}

    public record ConfiguracionConsolidacionResponse(int maxAsignadosConsolidador) {}
}
