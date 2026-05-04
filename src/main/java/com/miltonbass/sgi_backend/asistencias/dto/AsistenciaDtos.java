package com.miltonbass.sgi_backend.asistencias.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AsistenciaDtos {

    private AsistenciaDtos() {}

    // ─── Requests ────────────────────────────────────────────────────────

    public record RegistrarAsistenciaRequest(
            UUID miembroId,

            @Size(max = 150)
            String visitanteNombre,

            @Size(max = 20)
            String visitanteTelefono,

            String observacion) {}

    // ─── Responses ───────────────────────────────────────────────────────

    public record AsistenciaResponse(
            UUID id,
            UUID eventoId,
            UUID miembroId,
            String miembroNombres,
            String miembroApellidos,
            String visitanteNombre,
            String visitanteTelefono,
            boolean presente,
            String observacion,
            Instant creadoEn) {}

    public record CheckInListResponse(
            List<AsistenciaResponse> asistencias,
            int totalPresentes,
            Integer capacidad,
            String eventoTitulo,
            String eventoEstado) {}

    public record MiembroCheckInResponse(
            UUID id,
            String nombres,
            String apellidos,
            String telefono,
            boolean yaRegistrado) {}
}
