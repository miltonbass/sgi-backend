package com.miltonbass.sgi_backend.reportes.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.reportes.dto.DashboardSedeDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DashboardSedeService {

    private static final String ESTADOS_INACTIVOS =
            "'INACTIVO','RETIRADO','TRANSFERIDO','FALLECIDO'";

    private final JdbcTemplate jdbc;

    public DashboardSedeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DashboardSedeResponse obtener(LocalDate fechaDesde, LocalDate fechaHasta) {
        String tenant = tenant();
        long dias = ChronoUnit.DAYS.between(fechaDesde, fechaHasta);
        String granularidad = dias > 90 ? "MES" : "DIA";

        return new DashboardSedeResponse(
                calcularKpis(tenant),
                calcularCrecimiento(tenant, fechaDesde, fechaHasta, granularidad),
                granularidad,
                fechaDesde,
                fechaHasta,
                proximosEventos(tenant),
                alertasSinContacto(tenant));
    }

    // ── KPIs ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private KpisSede calcularKpis(String tenant) {
        Integer activos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros "
                + "WHERE estado NOT IN (" + ESTADOS_INACTIVOS + ") AND deleted_at IS NULL",
                Integer.class);

        Integer nuevosMes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros "
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
            """.formatted(tenant, ESTADOS_INACTIVOS, tenant, tenant);
        Double asistencia = jdbc.queryForObject(asistSql, Double.class);
        if (asistencia != null) asistencia = Math.round(asistencia * 10) / 10.0;

        Integer enConsolidacion = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros "
                + "WHERE estado = 'VISITOR' AND consolidador_id IS NOT NULL AND deleted_at IS NULL",
                Integer.class);

        return new KpisSede(
                activos         != null ? activos         : 0,
                nuevosMes       != null ? nuevosMes       : 0,
                asistencia,
                enConsolidacion != null ? enConsolidacion : 0);
    }

    // ── Crecimiento (granularidad automática) ─────────────────────────────────

    @SuppressWarnings("null")
    private List<CrecimientoItem> calcularCrecimiento(String tenant,
                                                       LocalDate desde,
                                                       LocalDate hasta,
                                                       String granularidad) {
        if ("MES".equals(granularidad)) {
            return crecimientoMensual(tenant, desde, hasta);
        }
        return crecimientoDiario(tenant, desde, hasta);
    }

    private List<CrecimientoItem> crecimientoMensual(String tenant,
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
            """.formatted(tenant, ESTADOS_INACTIVOS, tenant);

        DateTimeFormatter fmtMes = DateTimeFormatter.ofPattern("MMMM yyyy",
                Locale.forLanguageTag("es"));

        return jdbc.query(sql, (rs, i) -> {
            String periodo = rs.getString("periodo");
            LocalDate d = LocalDate.parse(periodo + "-01");
            String raw = d.format(fmtMes);
            String label = raw.substring(0, 1).toUpperCase() + raw.substring(1);
            return new CrecimientoItem(periodo, label,
                    rs.getInt("total_miembros"), rs.getInt("nuevos"));
        }, Date.valueOf(desde), Date.valueOf(hasta));
    }

    private List<CrecimientoItem> crecimientoDiario(String tenant,
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
            """.formatted(tenant, ESTADOS_INACTIVOS, tenant);

        DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("d MMM",
                Locale.forLanguageTag("es"));

        return jdbc.query(sql, (rs, i) -> {
            String periodo = rs.getString("periodo");
            LocalDate d = LocalDate.parse(periodo);
            String raw = d.format(fmtDia);
            String label = raw.substring(0, 1).toUpperCase() + raw.substring(1);
            return new CrecimientoItem(periodo, label,
                    rs.getInt("total_miembros"), rs.getInt("nuevos"));
        }, Date.valueOf(desde), Date.valueOf(hasta));
    }

    // ── Próximos eventos ─────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private List<EventoProximo> proximosEventos(String tenant) {
        String sql = """
            SELECT id, titulo, tipo, fecha_inicio, lugar
            FROM %s.eventos
            WHERE fecha_inicio > NOW()
              AND estado IN ('PROGRAMADO','ABIERTO')
              AND activo = TRUE
              AND deleted_at IS NULL
            ORDER BY fecha_inicio
            LIMIT 3
            """.formatted(tenant);

        return jdbc.query(sql, this::mapEvento);
    }

    private EventoProximo mapEvento(ResultSet rs, int i) throws SQLException {
        Timestamp ts = rs.getTimestamp("fecha_inicio");
        return new EventoProximo(
                rs.getObject("id", UUID.class),
                rs.getString("titulo"),
                rs.getString("tipo"),
                ts != null ? ts.toLocalDateTime() : null,
                rs.getString("lugar"));
    }

    // ── Top 5 alertas sin contacto ────────────────────────────────────────────

    @SuppressWarnings("null")
    private List<AlertaSinContacto> alertasSinContacto(String tenant) {
        String sql = """
            SELECT
              m.id AS miembro_id,
              m.nombres, m.apellidos, m.telefono,
              lc.ultima_fecha,
              CASE WHEN lc.ultima_fecha IS NOT NULL
                   THEN (CURRENT_DATE - lc.ultima_fecha)::int
                   ELSE NULL END AS dias_sin_contacto
            FROM %s.miembros m
            LEFT JOIN LATERAL (
              SELECT MAX(fecha) AS ultima_fecha
              FROM %s.contactos_consolidacion
              WHERE miembro_id = m.id
            ) lc ON TRUE
            WHERE m.consolidador_id IS NOT NULL
              AND m.deleted_at IS NULL
              AND m.estado NOT IN (%s)
            ORDER BY
              lc.ultima_fecha ASC NULLS FIRST,
              dias_sin_contacto DESC NULLS FIRST
            LIMIT 5
            """.formatted(tenant, tenant, ESTADOS_INACTIVOS);

        return jdbc.query(sql, (rs, i) -> {
            Date ultFecha = rs.getDate("ultima_fecha");
            Object diasObj = rs.getObject("dias_sin_contacto");
            return new AlertaSinContacto(
                    rs.getObject("miembro_id", UUID.class),
                    rs.getString("nombres"),
                    rs.getString("apellidos"),
                    rs.getString("telefono"),
                    diasObj != null ? ((Number) diasObj).intValue() : null,
                    ultFecha != null ? ultFecha.toLocalDate() : null);
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) throw new IllegalStateException("No hay tenant activo");
        return t;
    }
}
