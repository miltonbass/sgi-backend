package com.miltonbass.sgi_backend.consolidacion.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.consolidacion.dto.DashboardDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Dashboard personal del consolidador.
     *
     * @param usuarioId  userId del JWT (usuarios_sistema.id)
     * @param estado     filtro por estado del miembro (null = todos)
     * @param prioridad  ALTA | MEDIA | BAJA | null = todos
     * @param soloVencidas true = solo miembros con próxima acción vencida
     * @param page       página base-0
     * @param size       tamaño de página
     */
    public DashboardResponse obtener(UUID usuarioId, String estado,
                                     String prioridad, boolean soloVencidas,
                                     int page, int size) {
        String tenant = tenant();
        UUID consolidadorMiembroId = buscarMiembroId(tenant, usuarioId);

        if (consolidadorMiembroId == null) {
            DashboardResumen vacio = new DashboardResumen(0, 0, 0, 0);
            return new DashboardResponse(vacio, List.of(), page, size, 0, 0);
        }

        DashboardResumen resumen = calcularResumen(tenant, consolidadorMiembroId);
        List<MiembroConsolidacionItem> todos = fetchMiembros(tenant, consolidadorMiembroId);

        // Filtros en memoria (dataset pequeño — max_asignados_consolidador ≤ 100)
        List<MiembroConsolidacionItem> filtrados = todos.stream()
                .filter(m -> estado == null || estado.isBlank() || estado.equalsIgnoreCase(m.estado()))
                .filter(m -> prioridad == null || prioridad.isBlank() || prioridad.equalsIgnoreCase(m.prioridad()))
                .filter(m -> !soloVencidas || m.accionVencida())
                .toList();

        long total = filtrados.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        int from = Math.min(page * size, filtrados.size());
        int to   = Math.min(from + size, filtrados.size());
        List<MiembroConsolidacionItem> pagina = filtrados.subList(from, to);

        return new DashboardResponse(resumen, pagina, page, size, total, totalPages);
    }

    // ── Resumen numérico ──────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private DashboardResumen calcularResumen(String tenant, UUID consolidadorMiembroId) {
        String sql = """
            SELECT
              COUNT(*) AS total,
              COUNT(*) FILTER (
                WHERE lc.fecha IS NULL OR (CURRENT_DATE - lc.fecha) > 7
              ) AS sin_contacto_reciente,
              COUNT(*) FILTER (
                WHERE lc.fecha_proxima_accion IS NOT NULL
                  AND lc.fecha_proxima_accion < CURRENT_DATE
              ) AS con_accion_vencida
            FROM %s.miembros m
            LEFT JOIN LATERAL (
              SELECT fecha, fecha_proxima_accion
              FROM %s.contactos_consolidacion
              WHERE miembro_id = m.id AND consolidador_id = ?
              ORDER BY fecha DESC, created_at DESC LIMIT 1
            ) lc ON TRUE
            WHERE m.consolidador_id = ? AND m.deleted_at IS NULL
            """.formatted(tenant, tenant);

        Integer contactosHoy = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".contactos_consolidacion "
                + "WHERE consolidador_id = ? AND fecha = CURRENT_DATE",
                Integer.class, consolidadorMiembroId);

        return jdbc.queryForObject(sql, (rs, i) -> new DashboardResumen(
                rs.getInt("total"),
                rs.getInt("sin_contacto_reciente"),
                rs.getInt("con_accion_vencida"),
                contactosHoy != null ? contactosHoy : 0),
                consolidadorMiembroId, consolidadorMiembroId);
    }

    // ── Lista completa (sin paginar — dataset acotado por max_asignados) ──────

    @SuppressWarnings("null")
    private List<MiembroConsolidacionItem> fetchMiembros(String tenant, UUID consolidadorMiembroId) {
        String sql = """
            SELECT
              m.id           AS miembro_id,
              m.nombres, m.apellidos, m.telefono, m.estado,
              lc.fecha       AS ult_fecha,
              lc.tipo        AS ult_tipo,
              lc.resumen     AS ult_resumen,
              lc.proxima_accion,
              lc.fecha_proxima_accion,
              CASE WHEN lc.fecha IS NOT NULL
                   THEN (CURRENT_DATE - lc.fecha)::INT
                   ELSE NULL
              END AS dias_sin_contacto,
              CASE WHEN lc.fecha_proxima_accion IS NOT NULL
                        AND lc.fecha_proxima_accion < CURRENT_DATE
                   THEN TRUE ELSE FALSE
              END AS accion_vencida
            FROM %s.miembros m
            LEFT JOIN LATERAL (
              SELECT fecha, tipo, resumen, proxima_accion, fecha_proxima_accion
              FROM %s.contactos_consolidacion
              WHERE miembro_id = m.id AND consolidador_id = ?
              ORDER BY fecha DESC, created_at DESC LIMIT 1
            ) lc ON TRUE
            WHERE m.consolidador_id = ? AND m.deleted_at IS NULL
            ORDER BY
              CASE
                WHEN lc.fecha IS NULL OR (CURRENT_DATE - lc.fecha) > 14
                     OR (lc.fecha_proxima_accion IS NOT NULL AND lc.fecha_proxima_accion < CURRENT_DATE)
                THEN 1
                WHEN (CURRENT_DATE - lc.fecha) > 7 THEN 2
                ELSE 3
              END,
              dias_sin_contacto DESC NULLS FIRST
            """.formatted(tenant, tenant);

        return jdbc.query(sql, this::mapItem, consolidadorMiembroId, consolidadorMiembroId);
    }

    private MiembroConsolidacionItem mapItem(ResultSet rs, int i) throws SQLException {
        Integer dias = rs.getObject("dias_sin_contacto") != null
                ? rs.getInt("dias_sin_contacto") : null;
        boolean vencida  = rs.getBoolean("accion_vencida");
        boolean alerta   = dias == null || dias > 7;

        String prioridad;
        if (dias == null || dias > 14 || vencida) prioridad = "ALTA";
        else if (dias > 7)                         prioridad = "MEDIA";
        else                                       prioridad = "BAJA";

        Date ultFecha  = rs.getDate("ult_fecha");
        Date proxFecha = rs.getDate("fecha_proxima_accion");

        return new MiembroConsolidacionItem(
                rs.getObject("miembro_id", UUID.class),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("telefono"),
                rs.getString("estado"),
                prioridad,
                ultFecha  != null ? ultFecha.toLocalDate()  : null,
                rs.getString("ult_tipo"),
                rs.getString("ult_resumen"),
                dias,
                rs.getString("proxima_accion"),
                proxFecha != null ? proxFecha.toLocalDate() : null,
                vencida,
                alerta);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID buscarMiembroId(String tenant, UUID usuarioId) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM " + tenant + ".miembros WHERE usuario_id = ? AND deleted_at IS NULL",
                UUID.class, usuarioId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) throw new IllegalStateException("No hay tenant activo");
        return t;
    }
}
