package com.miltonbass.sgi_backend.consolidacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ContactoDtos {

    private ContactoDtos() {}

    // ─── Requests ────────────────────────────────────────────────────────────

    public record RegistrarContactoRequest(
            @NotNull
            LocalDate fecha,

            @NotBlank
            @Pattern(regexp = "LLAMADA|VISITA|MENSAJE|REUNION",
                     message = "tipo debe ser LLAMADA, VISITA, MENSAJE o REUNION")
            String tipo,

            @NotBlank @Size(max = 2000)
            String resumen,

            @Size(max = 500)
            String proximaAccion,

            LocalDate fechaProximaAccion,

            boolean recordatorioActivo) {}

    // ─── Responses ───────────────────────────────────────────────────────────

    public record ContactoResponse(
            UUID id,
            UUID miembroId,
            UUID consolidadorId,
            LocalDate fecha,
            String tipo,
            String resumen,
            String proximaAccion,
            LocalDate fechaProximaAccion,
            boolean recordatorioActivo,
            Instant creadoEn) {}

    public record ContactoPageResponse(
            List<ContactoResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages) {}

    public record ResumenContactosResponse(
            int totalContactos,
            /** Null si no hay ningún contacto registrado */
            Integer diasDesdeUltimoContacto,
            ContactoResponse ultimoContacto,
            /** Próxima acción pendiente del último contacto */
            String proximaAccionPendiente,
            LocalDate fechaProximaAccion) {}
}
