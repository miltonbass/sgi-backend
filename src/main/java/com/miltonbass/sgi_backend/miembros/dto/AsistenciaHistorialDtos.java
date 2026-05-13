package com.miltonbass.sgi_backend.miembros.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AsistenciaHistorialDtos {

    private AsistenciaHistorialDtos() {}

    public record MiembroResumen(UUID id, String nombres, String apellidos) {}

    public record ResumenAsistencia(
            int total,
            int presentes,
            int ausentes,
            Double porcentaje,
            int rachaActual,
            String rachaLabel) {}

    public record EventoAsistencia(
            UUID eventoId,
            String titulo,
            String tipo,
            LocalDate fecha,
            boolean presente,
            String observacion) {}

    public record AsistenciaHistorialResponse(
            MiembroResumen miembro,
            ResumenAsistencia resumen,
            List<EventoAsistencia> historial) {}
}
