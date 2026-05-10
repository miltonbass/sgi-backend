package com.miltonbass.sgi_backend.consolidacion.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReporteDtos {

    private ReporteDtos() {}

    public record ReporteResumen(
            /** Visitantes con consolidador asignado aún en estado VISITOR */
            int visitantesEnProceso,
            /** Conversiones VISITOR→MIEMBRO dentro del rango de fechas */
            int convertidosEnPeriodo,
            /** (convertidos / (visitantes + convertidos)) * 100 */
            double tasaConversion,
            /** Promedio de días desde el primer contacto hasta la conversión; null si no hay datos */
            Integer tiempoPromedioConsolidacion) {}

    public record ConsolidadorMetrica(
            UUID consolidadorId,
            String nombres,
            String apellidos,
            int totalAsignados,
            /** Miembros distintos contactados al menos una vez en el rango */
            int contactadosEnPeriodo,
            /** totalAsignados - contactadosEnPeriodo */
            int pendientes) {}

    public record ReporteConsolidacionResponse(
            ReporteResumen resumen,
            List<ConsolidadorMetrica> consolidadores,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {}
}
