package com.miltonbass.sgi_backend.eventos.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EventoDtos {

    private EventoDtos() {}

    // ─── Requests ────────────────────────────────────────────────────────

    public record CreateEventoRequest(
            @NotBlank @Size(max = 200)
            String titulo,

            @NotNull
            @Pattern(regexp = "CULTO|REUNION|CONFERENCIA|ESPECIAL",
                     message = "tipo debe ser CULTO, REUNION, CONFERENCIA o ESPECIAL")
            String tipo,

            String descripcion,

            @NotNull
            Instant fechaInicio,

            Instant fechaFin,

            @Size(max = 200)
            String lugar,

            @Min(1)
            Integer capacidad,

            boolean recurrente,

            /**
             * Solo requerido si recurrente=true.
             * Ejemplo: { "frecuencia": "SEMANAL", "diaSemana": "DOMINGO", "hasta": "2026-12-31" }
             * frecuencia: DIARIA | SEMANAL | QUINCENAL | MENSUAL
             * diaSemana: LUNES ... DOMINGO (solo para SEMANAL/QUINCENAL)
             */
            Map<String, Object> patronRecurrencia) {}

    public record UpdateEventoRequest(
            @Size(max = 200) String titulo,
            @Pattern(regexp = "CULTO|REUNION|CONFERENCIA|ESPECIAL") String tipo,
            String descripcion,
            Instant fechaInicio,
            Instant fechaFin,
            @Size(max = 200) String lugar,
            @Min(1) Integer capacidad,
            Boolean recurrente,
            Map<String, Object> patronRecurrencia) {}

    public record CambiarEstadoEventoRequest(
            @NotNull
            @Pattern(regexp = "PROGRAMADO|ABIERTO|CERRADO|CANCELADO",
                     message = "estado debe ser PROGRAMADO, ABIERTO, CERRADO o CANCELADO")
            String estado) {}

    // ─── Responses ───────────────────────────────────────────────────────

    public record EventoResponse(
            UUID id,
            UUID sedeId,
            String titulo,
            String tipo,
            String estado,
            String descripcion,
            Instant fechaInicio,
            Instant fechaFin,
            String lugar,
            Integer capacidad,
            boolean recurrente,
            Map<String, Object> patronRecurrencia,
            UUID creadoPor,
            Instant creadoEn,
            Instant actualizadoEn) {}

    public record EventoPageResponse(
            List<EventoResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages) {}
}
