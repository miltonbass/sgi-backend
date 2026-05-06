package com.miltonbass.sgi_backend.alertas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AlertaDtos {

    private AlertaDtos() {}

    // ─── Requests ────────────────────────────────────────────────────────

    public record GestionarAlertaRequest(String notas) {}

    public record ConfiguracionAlertaRequest(
            @NotNull @Min(1) @Max(52)
            Integer umbralSemanas) {}

    // ─── Responses ───────────────────────────────────────────────────────

    public record AlertaResponse(
            UUID id,
            UUID miembroId,
            String miembroNombres,
            String miembroApellidos,
            String miembroTelefono,
            UUID consolidadorId,
            String consolidadorNombres,
            String consolidadorApellidos,
            int semanasAusente,
            String estado,
            String notas,
            Instant creadoEn,
            Instant gestionadaEn) {}

    public record AlertaPageResponse(
            List<AlertaResponse> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages) {}

    public record DeteccionResult(
            int alertasCreadas,
            int miembrosEvaluados,
            int umbralSemanas) {}

    public record ConfiguracionAlertaResponse(int umbralSemanas) {}
}
