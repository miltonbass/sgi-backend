package com.miltonbass.sgi_backend.reportes.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResumenDtos {

    private ResumenDtos() {}

    public record EventoResumenResponse(
            UUID eventoId,
            String titulo,
            String tipo,
            String estado,
            Instant fechaInicio,
            Instant fechaFin,
            Integer capacidad,
            int totalPresentes,
            Double porcentajeCapacidad,
            int primerasVisitas,
            int miembrosActivos,
            int miembrosOtros) {}

    public record ComparativaItem(
            UUID eventoId,
            String titulo,
            Instant fechaInicio,
            Integer capacidad,
            int totalPresentes,
            Double porcentajeCapacidad) {}

    public record ComparativaResponse(
            EventoResumenResponse eventoActual,
            List<ComparativaItem> ultimasCuatroSemanas) {}

    public record PresenciaExportRow(
            String nombreCompleto,
            String numeroMiembro,
            String estadoMiembro,
            String telefono,
            String observacion,
            Instant registradoEn) {}
}
