package com.miltonbass.sgi_backend.reportes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class DashboardSedeDtos {

    private DashboardSedeDtos() {}

    public record KpisSede(
            int totalMiembrosActivos,
            /** Nuevos en el mes calendario actual (snapshot fijo) */
            int nuevosMes,
            /** % asistencia promedio sobre los últimos 4 cultos cerrados; null si no hay datos */
            Double asistenciaPromedio,
            int miembrosEnConsolidacion) {}

    public record CrecimientoItem(
            /** "YYYY-MM" si granularidad=MES | "YYYY-MM-DD" si granularidad=DIA */
            String periodo,
            /** "Mayo 2026" (MES) | "11 Mayo" (DIA) */
            String label,
            /** Miembros activos al cierre del periodo */
            int totalMiembros,
            /** Nuevos registrados en ese periodo */
            int nuevos) {}

    public record EventoProximo(
            UUID id,
            String titulo,
            String tipo,
            LocalDateTime fechaInicio,
            String lugar) {}

    public record AlertaSinContacto(
            UUID miembroId,
            String nombres,
            String apellidos,
            String telefono,
            /** null = nunca contactado */
            Integer diasSinContacto,
            /** null = nunca contactado */
            LocalDate ultimoContacto) {}

    public record DashboardSedeResponse(
            KpisSede kpis,
            List<CrecimientoItem> crecimiento,
            /** "MES" (rango > 90 días) | "DIA" (rango ≤ 90 días) */
            String granularidad,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            List<EventoProximo> proximosEventos,
            List<AlertaSinContacto> alertasSinContacto) {}
}
