package com.miltonbass.sgi_backend.alertas.service;

import com.miltonbass.sgi_backend.alertas.dto.AlertaDtos.*;
import com.miltonbass.sgi_backend.config.TenantContext;
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
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);

    private final JdbcTemplate jdbc;

    public AlertaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Detectar ausencias y crear alertas ────────────────────────────────────
    public DeteccionResult detectar() {
        String tenant = tenant();
        int umbral = obtenerUmbral(tenant);
        int dias = umbral * 7;

        // Miembros activos ausentes en el período sin alerta PENDIENTE preexistente
        String sqlAusentes = "SELECT m.id, m.nombres, m.apellidos, m.consolidador_id, s.id AS sede_id "
                + "FROM " + tenant + ".miembros m "
                + "JOIN shared.sedes s ON s.schema_name = ? "
                + "WHERE m.deleted_at IS NULL "
                + "AND m.estado NOT IN ('INACTIVO','RETIRADO','TRANSFERIDO','FALLECIDO','VISITOR') "
                + "AND NOT EXISTS ( "
                + "    SELECT 1 FROM " + tenant + ".asistencias a "
                + "    JOIN " + tenant + ".eventos e ON a.evento_id = e.id "
                + "    WHERE a.miembro_id = m.id AND a.presente = TRUE "
                + "    AND e.fecha_inicio >= NOW() - ('" + dias + " days')::INTERVAL "
                + ") "
                + "AND NOT EXISTS ( "
                + "    SELECT 1 FROM " + tenant + ".alertas_ausencia al "
                + "    WHERE al.miembro_id = m.id AND al.estado = 'PENDIENTE' "
                + ")";

        List<UUID[]> ausentes = jdbc.query(sqlAusentes, (rs, i) -> new UUID[]{
                rs.getObject("id", UUID.class),
                rs.getObject("consolidador_id", UUID.class),
                rs.getObject("sede_id", UUID.class)
        }, tenant);

        int creadas = 0;
        for (UUID[] row : ausentes) {
            UUID miembroId      = row[0];
            UUID consolidadorId = row[1];
            UUID sedeId         = row[2];

            UUID alertaId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO " + tenant + ".alertas_ausencia "
                    + "(id, sede_id, miembro_id, semanas_ausente, consolidador_id) "
                    + "VALUES (?,?,?,?,?)",
                    alertaId, sedeId, miembroId, umbral, consolidadorId);

            crearTareaSeguimiento(tenant, alertaId, miembroId, consolidadorId, sedeId, umbral);
            creadas++;
        }

        // Miembros activos totales para estadística
        Integer totalActivos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros "
                + "WHERE deleted_at IS NULL "
                + "AND estado NOT IN ('INACTIVO','RETIRADO','TRANSFERIDO','FALLECIDO','VISITOR')",
                Integer.class);

        log.info("[Alertas] Detección: {} alertas creadas de {} miembros activos | umbral={}s",
                creadas, totalActivos, umbral);
        return new DeteccionResult(creadas, totalActivos != null ? totalActivos : 0, umbral);
    }

    // ── Listar alertas ────────────────────────────────────────────────────────
    public AlertaPageResponse listar(String estado, int page, int size) {
        String tenant = tenant();
        String whereEstado = (estado != null && !estado.isBlank()) ? "AND a.estado = '" + estado.toUpperCase() + "' " : "";

        String countSql = "SELECT COUNT(*) FROM " + tenant + ".alertas_ausencia a WHERE 1=1 " + whereEstado;
        Long total = jdbc.queryForObject(countSql, Long.class);
        if (total == null) total = 0L;

        String dataSql = "SELECT a.id, a.miembro_id, a.consolidador_id, a.semanas_ausente, "
                + "a.estado, a.notas, a.created_at, a.gestionada_en, "
                + "m.nombres, m.apellidos, m.telefono, "
                + "c.nombres AS con_nombres, c.apellidos AS con_apellidos "
                + "FROM " + tenant + ".alertas_ausencia a "
                + "JOIN " + tenant + ".miembros m ON a.miembro_id = m.id "
                + "LEFT JOIN " + tenant + ".miembros c ON a.consolidador_id = c.id "
                + "WHERE 1=1 " + whereEstado
                + "ORDER BY a.created_at DESC LIMIT ? OFFSET ?";

        List<AlertaResponse> content = jdbc.query(dataSql, this::mapRow, size, (long) page * size);
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new AlertaPageResponse(content, page, size, total, totalPages);
    }

    // ── Obtener alerta por ID ─────────────────────────────────────────────────
    public AlertaResponse obtener(UUID id) {
        String tenant = tenant();
        return jdbc.query(
                "SELECT a.id, a.miembro_id, a.consolidador_id, a.semanas_ausente, "
                + "a.estado, a.notas, a.created_at, a.gestionada_en, "
                + "m.nombres, m.apellidos, m.telefono, "
                + "c.nombres AS con_nombres, c.apellidos AS con_apellidos "
                + "FROM " + tenant + ".alertas_ausencia a "
                + "JOIN " + tenant + ".miembros m ON a.miembro_id = m.id "
                + "LEFT JOIN " + tenant + ".miembros c ON a.consolidador_id = c.id "
                + "WHERE a.id = ?",
                this::mapRow, id)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada: " + id));
    }

    // ── Gestionar alerta ──────────────────────────────────────────────────────
    public AlertaResponse gestionar(UUID id, GestionarAlertaRequest req, UUID usuarioId) {
        String tenant = tenant();
        AlertaResponse actual = obtener(id);
        if (!"PENDIENTE".equals(actual.estado())) {
            throw new IllegalArgumentException("Solo se pueden gestionar alertas en estado PENDIENTE");
        }
        jdbc.update(
                "UPDATE " + tenant + ".alertas_ausencia "
                + "SET estado='GESTIONADA', notas=?, gestionada_por=?, gestionada_en=NOW() WHERE id=?",
                req != null ? req.notas() : null, usuarioId, id);

        jdbc.update(
                "UPDATE " + tenant + ".tareas_seguimiento SET estado='COMPLETADA', completada_en=NOW() "
                + "WHERE alerta_id=? AND estado='PENDIENTE'",
                id);

        log.info("[Alertas] Gestionada: {} por usuario {}", id, usuarioId);
        return obtener(id);
    }

    // ── Descartar alerta ──────────────────────────────────────────────────────
    public AlertaResponse descartar(UUID id, GestionarAlertaRequest req, UUID usuarioId) {
        String tenant = tenant();
        AlertaResponse actual = obtener(id);
        if (!"PENDIENTE".equals(actual.estado())) {
            throw new IllegalArgumentException("Solo se pueden descartar alertas en estado PENDIENTE");
        }
        jdbc.update(
                "UPDATE " + tenant + ".alertas_ausencia "
                + "SET estado='DESCARTADA', notas=?, gestionada_por=?, gestionada_en=NOW() WHERE id=?",
                req != null ? req.notas() : null, usuarioId, id);

        log.info("[Alertas] Descartada: {} por usuario {}", id, usuarioId);
        return obtener(id);
    }

    // ── Configuración del umbral ──────────────────────────────────────────────
    public ConfiguracionAlertaResponse obtenerConfiguracion() {
        String tenant = tenant();
        Integer umbral = jdbc.queryForObject(
                "SELECT umbral_ausencia_semanas FROM shared.sedes WHERE schema_name = ?",
                Integer.class, tenant);
        return new ConfiguracionAlertaResponse(umbral != null ? umbral : 3);
    }

    public ConfiguracionAlertaResponse actualizarConfiguracion(ConfiguracionAlertaRequest req) {
        String tenant = tenant();
        jdbc.update(
                "UPDATE shared.sedes SET umbral_ausencia_semanas = ? WHERE schema_name = ?",
                req.umbralSemanas(), tenant);
        log.info("[Alertas] Umbral actualizado a {} semanas | sede={}", req.umbralSemanas(), tenant);
        return new ConfiguracionAlertaResponse(req.umbralSemanas());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void crearTareaSeguimiento(String tenant, UUID alertaId, UUID miembroId,
                                       UUID consolidadorId, UUID sedeId, int umbral) {
        String descripcion = "Seguimiento de ausencia: el miembro lleva " + umbral
                + " semanas sin asistir. Por favor contactar y brindar acompañamiento.";
        jdbc.update(
                "INSERT INTO " + tenant + ".tareas_seguimiento "
                + "(id, sede_id, alerta_id, miembro_id, asignado_a, descripcion) "
                + "VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), sedeId, alertaId, miembroId, consolidadorId, descripcion);
    }

    private int obtenerUmbral(String tenant) {
        Integer umbral = jdbc.queryForObject(
                "SELECT umbral_ausencia_semanas FROM shared.sedes WHERE schema_name = ?",
                Integer.class, tenant);
        return umbral != null ? umbral : 3;
    }

    private AlertaResponse mapRow(ResultSet rs, int i) throws SQLException {
        Timestamp cat = rs.getTimestamp("created_at");
        Timestamp gat = rs.getTimestamp("gestionada_en");
        return new AlertaResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("miembro_id", UUID.class),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("telefono"),
                rs.getObject("consolidador_id", UUID.class),
                rs.getString("con_nombres"),
                rs.getString("con_apellidos"),
                rs.getInt("semanas_ausente"),
                rs.getString("estado"),
                rs.getString("notas"),
                cat != null ? cat.toInstant() : null,
                gat != null ? gat.toInstant() : null);
    }

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) throw new IllegalStateException("No hay tenant activo");
        return t;
    }
}
