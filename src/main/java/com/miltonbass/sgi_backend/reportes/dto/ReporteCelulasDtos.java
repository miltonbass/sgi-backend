package com.miltonbass.sgi_backend.reportes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ReporteCelulasDtos {

    // ─── Vista árbol ──────────────────────────────────────────────────────────

    public record CelulaReporteItem(
            UUID id,
            String nombre,
            String tipo,
            UUID liderId,
            String liderNombre,
            UUID grupoPadreId,
            int nivel,
            boolean activo,
            int totalMiembros,
            long totalSesiones,
            Double promedioAsistencia,
            long totalVisitantes,
            BigDecimal totalOfrenda) {}

    public record ReporteCelulasResponse(
            List<CelulaReporteItem> celulas,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            int totalCelulas,
            long totalMiembros,
            BigDecimal totalOfrenda,
            Double promedioAsistenciaGeneral) {}

    // ─── Detalle de célula ────────────────────────────────────────────────────

    public record SesionDetalle(
            UUID id,
            LocalDate fecha,
            String tema,
            String lugar,
            int totalPresentes,
            int totalVisitantes,
            BigDecimal ofrendaMonto) {}

    public record MiembroAsistenciaItem(
            UUID id,
            String nombres,
            String apellidos,
            long sesionesAsistidas,
            long totalSesiones,
            double porcentajeAsistencia) {}

    public record DetalleCelulaResponse(
            UUID id,
            String nombre,
            String tipo,
            UUID liderId,
            String liderNombre,
            int nivel,
            int totalMiembros,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            List<SesionDetalle> sesiones,
            List<MiembroAsistenciaItem> miembros,
            BigDecimal totalOfrenda,
            long totalVisitantes,
            Double promedioAsistencia) {}
}
