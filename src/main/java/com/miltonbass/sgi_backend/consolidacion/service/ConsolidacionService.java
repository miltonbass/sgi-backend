package com.miltonbass.sgi_backend.consolidacion.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.consolidacion.dto.ConsolidacionDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class ConsolidacionService {

    private static final Logger log = LoggerFactory.getLogger(ConsolidacionService.class);

    private final JdbcTemplate jdbc;

    public ConsolidacionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Listar consolidadores con carga actual ────────────────────────────
    public List<ConsolidadorResponse> listarConsolidadores() {
        String tenant = tenant();
        int maxAsignados = obtenerMaxAsignados(tenant);

        String sql = "SELECT m.id, m.nombres, m.apellidos, m.telefono, "
                + "(SELECT COUNT(*) FROM " + tenant + ".miembros a "
                + " WHERE a.consolidador_id = m.id AND a.deleted_at IS NULL "
                + " AND a.estado NOT IN ('INACTIVO','RETIRADO','TRANSFERIDO','FALLECIDO')) AS asignados_activos "
                + "FROM " + tenant + ".miembros m "
                + "JOIN shared.usuarios_sedes us ON us.usuario_id = m.usuario_id "
                + "JOIN shared.sedes s ON s.schema_name = ? "
                + "WHERE m.deleted_at IS NULL "
                + "AND us.sede_id = s.id AND us.activo = TRUE "
                + "AND 'CONSOLIDACION_SEDE' = ANY(us.roles) "
                + "ORDER BY m.apellidos, m.nombres";

        return jdbc.query(sql, (rs, i) -> new ConsolidadorResponse(
                rs.getObject("id", UUID.class),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("telefono"),
                rs.getInt("asignados_activos"),
                maxAsignados), tenant);
    }

    // ── Listar tareas de consolidación ────────────────────────────────────
    public TareaConsolidacionPageResponse listarTareas(
            UUID consolidadorId, String estado, UUID userId, int page, int size) {
        String tenant = tenant();

        // Si el llamante es solo CONSOLIDACION_SEDE, ignorar consolidadorId del query
        // y filtrar por su propio miembro_id derivado del userId del token
        if (userId != null) {
            List<UUID> ids = jdbc.query(
                    "SELECT id FROM " + tenant + ".miembros WHERE usuario_id = ? AND deleted_at IS NULL",
                    (rs, i) -> rs.getObject("id", UUID.class), userId);
            consolidadorId = ids.isEmpty() ? null : ids.get(0);
        }

        String whereEstado = (estado != null && !estado.isBlank())
                ? "AND t.estado = '" + estado.toUpperCase() + "' " : "";
        String whereCons = consolidadorId != null
                ? "AND t.consolidador_id = '" + consolidadorId + "' " : "";

        String countSql = "SELECT COUNT(*) FROM " + tenant + ".tareas_consolidacion t WHERE 1=1 "
                + whereEstado + whereCons;
        Long total = jdbc.queryForObject(countSql, Long.class);
        if (total == null) total = 0L;

        String dataSql = "SELECT t.id, t.miembro_id, m.nombres AS miem_nombres, "
                + "m.apellidos AS miem_apellidos, m.telefono AS miem_telefono, "
                + "t.consolidador_id, c.nombres AS con_nombres, c.apellidos AS con_apellidos, "
                + "t.tipo, t.estado, t.descripcion, t.notas, t.created_at, t.completada_en "
                + "FROM " + tenant + ".tareas_consolidacion t "
                + "JOIN " + tenant + ".miembros m ON t.miembro_id = m.id "
                + "LEFT JOIN " + tenant + ".miembros c ON t.consolidador_id = c.id "
                + "WHERE 1=1 " + whereEstado + whereCons
                + "ORDER BY t.created_at DESC LIMIT ? OFFSET ?";

        List<TareaConsolidacionResponse> content = jdbc.query(dataSql, this::mapTarea,
                size, (long) page * size);
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new TareaConsolidacionPageResponse(content, page, size, total, totalPages);
    }

    // ── Completar tarea ───────────────────────────────────────────────────
    public TareaConsolidacionResponse completarTarea(UUID tareaId, UUID usuarioId, String notas) {
        String tenant = tenant();

        String estadoActual = jdbc.queryForObject(
                "SELECT estado FROM " + tenant + ".tareas_consolidacion WHERE id = ?",
                String.class, tareaId);
        if (estadoActual == null) {
            throw new IllegalArgumentException("Tarea no encontrada: " + tareaId);
        }
        if (!"PENDIENTE".equals(estadoActual)) {
            throw new IllegalArgumentException("Solo se pueden completar tareas en estado PENDIENTE");
        }

        jdbc.update(
                "UPDATE " + tenant + ".tareas_consolidacion "
                + "SET estado='COMPLETADA', notas=?, completada_por=?, completada_en=NOW() WHERE id=?",
                notas, usuarioId, tareaId);

        log.info("[Consolidacion] Tarea completada: {} por usuario {}", tareaId, usuarioId);
        return obtenerTarea(tenant, tareaId);
    }

    // ── Configuración ─────────────────────────────────────────────────────
    public ConfiguracionConsolidacionResponse obtenerConfiguracion() {
        return new ConfiguracionConsolidacionResponse(obtenerMaxAsignados(tenant()));
    }

    public ConfiguracionConsolidacionResponse actualizarConfiguracion(
            ConfiguracionConsolidacionRequest req) {
        String tenant = tenant();
        jdbc.update(
                "UPDATE shared.sedes SET max_asignados_consolidador = ? WHERE schema_name = ?",
                req.maxAsignadosConsolidador(), tenant);
        log.info("[Consolidacion] Max asignados actualizado a {} | sede={}",
                req.maxAsignadosConsolidador(), tenant);
        return new ConfiguracionConsolidacionResponse(req.maxAsignadosConsolidador());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private TareaConsolidacionResponse obtenerTarea(String tenant, UUID tareaId) {
        return jdbc.query(
                "SELECT t.id, t.miembro_id, m.nombres AS miem_nombres, "
                + "m.apellidos AS miem_apellidos, m.telefono AS miem_telefono, "
                + "t.consolidador_id, c.nombres AS con_nombres, c.apellidos AS con_apellidos, "
                + "t.tipo, t.estado, t.descripcion, t.notas, t.created_at, t.completada_en "
                + "FROM " + tenant + ".tareas_consolidacion t "
                + "JOIN " + tenant + ".miembros m ON t.miembro_id = m.id "
                + "LEFT JOIN " + tenant + ".miembros c ON t.consolidador_id = c.id "
                + "WHERE t.id = ?",
                this::mapTarea, tareaId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada: " + tareaId));
    }

    private TareaConsolidacionResponse mapTarea(ResultSet rs, int i) throws SQLException {
        Timestamp cat = rs.getTimestamp("created_at");
        Timestamp cen = rs.getTimestamp("completada_en");
        return new TareaConsolidacionResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("miembro_id", UUID.class),
                rs.getString("miem_nombres"),
                rs.getString("miem_apellidos"),
                rs.getString("miem_telefono"),
                rs.getObject("consolidador_id", UUID.class),
                rs.getString("con_nombres"),
                rs.getString("con_apellidos"),
                rs.getString("tipo"),
                rs.getString("estado"),
                rs.getString("descripcion"),
                rs.getString("notas"),
                cat != null ? cat.toInstant() : null,
                cen != null ? cen.toInstant() : null);
    }

    private int obtenerMaxAsignados(String tenant) {
        Integer max = jdbc.queryForObject(
                "SELECT max_asignados_consolidador FROM shared.sedes WHERE schema_name = ?",
                Integer.class, tenant);
        return max != null ? max : 10;
    }

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) throw new IllegalStateException("No hay tenant activo");
        return t;
    }
}
