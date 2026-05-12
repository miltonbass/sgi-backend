package com.miltonbass.sgi_backend.reportes.service;

import com.miltonbass.sgi_backend.reportes.dto.DashboardGlobalDtos.*;
import com.miltonbass.sgi_backend.reportes.dto.DashboardSedeDtos.CrecimientoItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DashboardGlobalService {

    private static final String ESTADOS_INACTIVOS =
            "'INACTIVO','RETIRADO','TRANSFERIDO','FALLECIDO'";

    private final JdbcTemplate jdbc;

    public DashboardGlobalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DashboardGlobalResponse obtener(LocalDate fechaDesde, LocalDate fechaHasta) {
        long dias = ChronoUnit.DAYS.between(fechaDesde, fechaHasta);
        String granularidad = dias > 90 ? "MES" : "DIA";

        List<SedeStats> sedes = jdbc.query(
                "SELECT id, codigo, nombre, schema_name FROM shared.sedes "
                + "WHERE activa = TRUE AND deleted_at IS NULL ORDER BY nombre",
                (rs, i) -> {
                    String schema = rs.getString("schema_name");
                    return new SedeStats(
                            rs.getObject("id", UUID.class),
                            rs.getString("codigo"),
                            rs.getString("nombre"),
                            calcularKpis(schema),
                            calcularCrecimiento(schema, fechaDesde, fechaHasta, granularidad));
                });

        return new DashboardGlobalResponse(sedes, granularidad, fechaDesde, fechaHasta);
    }

    // ── KPIs por sede ─────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private KpisGlobal calcularKpis(String schema) {
        Integer activos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".miembros "
                + "WHERE estado NOT IN (" + ESTADOS_INACTIVOS + ") AND deleted_at IS NULL",
                Integer.class);

        Integer nuevosMes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".miembros "
                + "WHERE DATE_TRUNC('month', created_at) = DATE_TRUNC('month', NOW()) "
                + "AND deleted_at IS NULL",
                Integer.class);

        String asistSql = """
            SELECT AVG(asistidos * 100.0 / NULLIF(total_activos, 0))
            FROM (
              SELECT
                COUNT(a.id) FILTER (WHERE a.presente = TRUE AND a.miembro_id IS NOT NULL) AS asistidos,
                (SELECT COUNT(*) FROM %s.miembros
                 WHERE estado NOT IN (%s) AND deleted_at IS NULL) AS total_activos
              FROM %s.eventos e
              LEFT JOIN %s.asistencias a ON a.evento_id = e.id
              WHERE e.tipo = 'CULTO' AND e.estado = 'CERRADO' AND e.deleted_at IS NULL
              GROUP BY e.id
              ORDER BY e.fecha_inicio DESC
              LIMIT 4
            ) sub
            """.formatted(schema, ESTADOS_INACTIVOS, schema, schema);
        Double asistencia = jdbc.queryForObject(asistSql, Double.class);
        if (asistencia != null) asistencia = Math.round(asistencia * 10) / 10.0;

        Integer enConsolidacion = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".miembros "
                + "WHERE estado = 'VISITOR' AND consolidador_id IS NOT NULL AND deleted_at IS NULL",
                Integer.class);

        return new KpisGlobal(
                activos         != null ? activos         : 0,
                nuevosMes       != null ? nuevosMes       : 0,
                asistencia,
                enConsolidacion != null ? enConsolidacion : 0);
    }

    // ── Crecimiento por sede ──────────────────────────────────────────────────

    private List<CrecimientoItem> calcularCrecimiento(String schema,
                                                       LocalDate desde,
                                                       LocalDate hasta,
                                                       String granularidad) {
        return "MES".equals(granularidad)
                ? crecimientoMensual(schema, desde, hasta)
                : crecimientoDiario(schema, desde, hasta);
    }

    @SuppressWarnings("null")
    private List<CrecimientoItem> crecimientoMensual(String schema,
                                                      LocalDate desde,
                                                      LocalDate hasta) {
        String sql = """
            WITH meses AS (
              SELECT generate_series(
                DATE_TRUNC('month', ?::date),
                DATE_TRUNC('month', ?::date),
                INTERVAL '1 month'
              )::date AS mes
            )
            SELECT
              TO_CHAR(m.mes, 'YYYY-MM') AS periodo,
              (SELECT COUNT(*) FROM %s.miembros
               WHERE created_at::date <= (m.mes + INTERVAL '1 month - 1 day')::date
                 AND estado NOT IN (%s) AND deleted_at IS NULL) AS total_miembros,
              (SELECT COUNT(*) FROM %s.miembros
               WHERE created_at::date >= m.mes
                 AND created_at::date <= (m.mes + INTERVAL '1 month - 1 day')::date
                 AND deleted_at IS NULL) AS nuevos
            FROM meses m
            ORDER BY m.mes
            """.formatted(schema, ESTADOS_INACTIVOS, schema);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy",
                Locale.forLanguageTag("es"));

        return jdbc.query(sql, (rs, i) -> {
            String periodo = rs.getString("periodo");
            LocalDate d = LocalDate.parse(periodo + "-01");
            String raw = d.format(fmt);
            String label = raw.substring(0, 1).toUpperCase() + raw.substring(1);
            return new CrecimientoItem(periodo, label,
                    rs.getInt("total_miembros"), rs.getInt("nuevos"));
        }, Date.valueOf(desde), Date.valueOf(hasta));
    }

    @SuppressWarnings("null")
    private List<CrecimientoItem> crecimientoDiario(String schema,
                                                     LocalDate desde,
                                                     LocalDate hasta) {
        String sql = """
            WITH dias AS (
              SELECT generate_series(?::date, ?::date, INTERVAL '1 day')::date AS dia
            )
            SELECT
              TO_CHAR(d.dia, 'YYYY-MM-DD') AS periodo,
              (SELECT COUNT(*) FROM %s.miembros
               WHERE created_at::date <= d.dia
                 AND estado NOT IN (%s) AND deleted_at IS NULL) AS total_miembros,
              (SELECT COUNT(*) FROM %s.miembros
               WHERE created_at::date = d.dia
                 AND deleted_at IS NULL) AS nuevos
            FROM dias d
            ORDER BY d.dia
            """.formatted(schema, ESTADOS_INACTIVOS, schema);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM",
                Locale.forLanguageTag("es"));

        return jdbc.query(sql, (rs, i) -> {
            String periodo = rs.getString("periodo");
            LocalDate d = LocalDate.parse(periodo);
            String raw = d.format(fmt);
            String label = raw.substring(0, 1).toUpperCase() + raw.substring(1);
            return new CrecimientoItem(periodo, label,
                    rs.getInt("total_miembros"), rs.getInt("nuevos"));
        }, Date.valueOf(desde), Date.valueOf(hasta));
    }
}
