package com.miltonbass.sgi_backend.consolidacion.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.consolidacion.dto.ReporteDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ReporteService {

    private final JdbcTemplate jdbc;

    public ReporteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReporteConsolidacionResponse obtener(LocalDate fechaDesde, LocalDate fechaHasta) {
        String tenant = tenant();
        ReporteResumen resumen = calcularResumen(tenant, fechaDesde, fechaHasta);
        List<ConsolidadorMetrica> consolidadores = listarConsolidadores(tenant, fechaDesde, fechaHasta);
        return new ReporteConsolidacionResponse(resumen, consolidadores, fechaDesde, fechaHasta);
    }

    // ── Resumen de la sede ────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private ReporteResumen calcularResumen(String tenant, LocalDate fechaDesde, LocalDate fechaHasta) {
        Integer visitantes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros "
                + "WHERE estado = 'VISITOR' AND consolidador_id IS NOT NULL AND deleted_at IS NULL",
                Integer.class);

        String convSql = """
            SELECT COUNT(DISTINCT h.miembro_id)
            FROM %s.miembro_estado_historial h
            JOIN %s.miembros m ON m.id = h.miembro_id
            WHERE h.estado_nuevo  = 'MIEMBRO'
              AND h.estado_anterior = 'VISITOR'
              AND h.cambiado_en::date BETWEEN ? AND ?
              AND m.consolidador_id IS NOT NULL
              AND m.deleted_at IS NULL
            """.formatted(tenant, tenant);
        Integer convertidos = jdbc.queryForObject(convSql, Integer.class,
                Date.valueOf(fechaDesde), Date.valueOf(fechaHasta));

        int v = visitantes  != null ? visitantes  : 0;
        int c = convertidos != null ? convertidos : 0;
        double tasa = (v + c) > 0 ? Math.round((c * 1000.0 / (v + c))) / 10.0 : 0.0;

        String tiempoSql = """
            SELECT ROUND(AVG(h.cambiado_en::date - fc.primera_fecha))::int
            FROM %s.miembro_estado_historial h
            JOIN %s.miembros m ON m.id = h.miembro_id
            JOIN LATERAL (
              SELECT MIN(cc.fecha) AS primera_fecha
              FROM %s.contactos_consolidacion cc
              WHERE cc.miembro_id = h.miembro_id
            ) fc ON fc.primera_fecha IS NOT NULL
            WHERE h.estado_nuevo  = 'MIEMBRO'
              AND h.estado_anterior = 'VISITOR'
              AND h.cambiado_en::date BETWEEN ? AND ?
              AND m.consolidador_id IS NOT NULL
              AND m.deleted_at IS NULL
            """.formatted(tenant, tenant, tenant);
        Integer tiempoPromedio = jdbc.queryForObject(tiempoSql, Integer.class,
                Date.valueOf(fechaDesde), Date.valueOf(fechaHasta));

        return new ReporteResumen(v, c, tasa, tiempoPromedio);
    }

    // ── Métricas por consolidador ─────────────────────────────────────────────

    @SuppressWarnings("null")
    private List<ConsolidadorMetrica> listarConsolidadores(String tenant,
                                                            LocalDate fechaDesde,
                                                            LocalDate fechaHasta) {
        String sql = """
            SELECT
              c.id                          AS consolidador_id,
              c.nombres,
              c.apellidos,
              COUNT(DISTINCT m.id)          AS total_asignados,
              COUNT(DISTINCT cc.miembro_id)
                FILTER (WHERE cc.miembro_id IS NOT NULL) AS contactados_periodo
            FROM %s.miembros c
            LEFT JOIN %s.miembros m
              ON m.consolidador_id = c.id AND m.deleted_at IS NULL
            LEFT JOIN %s.contactos_consolidacion cc
              ON cc.consolidador_id = c.id
             AND cc.fecha BETWEEN ? AND ?
            WHERE c.deleted_at IS NULL
              AND c.usuario_id IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM shared.usuarios_sedes us_s
                WHERE us_s.usuario_id = c.usuario_id
                  AND us_s.sede_id = (SELECT id FROM shared.sedes WHERE schema_name = ?)
                  AND 'CONSOLIDACION_SEDE' = ANY(us_s.roles)
                  AND us_s.activo = TRUE
              )
            GROUP BY c.id, c.nombres, c.apellidos
            ORDER BY total_asignados DESC, c.apellidos
            """.formatted(tenant, tenant, tenant);

        return jdbc.query(sql, (rs, i) -> {
            int asignados   = rs.getInt("total_asignados");
            int contactados = rs.getInt("contactados_periodo");
            return new ConsolidadorMetrica(
                    rs.getObject("consolidador_id", UUID.class),
                    rs.getString("nombres"),
                    rs.getString("apellidos"),
                    asignados,
                    contactados,
                    Math.max(0, asignados - contactados));
        }, Date.valueOf(fechaDesde), Date.valueOf(fechaHasta), tenant);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) throw new IllegalStateException("No hay tenant activo");
        return t;
    }
}
