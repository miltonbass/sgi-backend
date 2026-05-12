package com.miltonbass.sgi_backend.reportes.dto;

import com.miltonbass.sgi_backend.reportes.dto.DashboardSedeDtos.CrecimientoItem;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DashboardGlobalDtos {

    private DashboardGlobalDtos() {}

    public record KpisGlobal(
            int totalMiembrosActivos,
            int nuevosMes,
            /** % asistencia promedio últimos 4 cultos cerrados; null si no hay datos */
            Double asistenciaPromedio,
            int miembrosEnConsolidacion) {}

    public record SedeStats(
            UUID sedeId,
            String sedeCodigo,
            String sedeNombre,
            KpisGlobal kpis,
            List<CrecimientoItem> crecimiento) {}

    public record DashboardGlobalResponse(
            List<SedeStats> sedes,
            /** "MES" (rango > 90 días) | "DIA" (rango <= 90 días) */
            String granularidad,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {}
}
