package com.miltonbass.sgi_backend.asistencias.service;

import com.miltonbass.sgi_backend.asistencias.dto.AsistenciaDtos.*;
import com.miltonbass.sgi_backend.config.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class AsistenciaService {

    private static final Logger log = LoggerFactory.getLogger(AsistenciaService.class);

    private final JdbcTemplate jdbc;

    public AsistenciaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Check-in list con contador ────────────────────────────────────────────
    public CheckInListResponse listar(UUID eventoId) {
        String tenant = tenant();

        String eventoSql = "SELECT titulo, capacidad, estado FROM " + tenant
                + ".eventos WHERE id = ? AND deleted_at IS NULL";
        var eventoRow = jdbc.queryForList(eventoSql, eventoId);
        if (eventoRow.isEmpty()) {
            throw new IllegalArgumentException("Evento no encontrado: " + eventoId);
        }
        String titulo    = (String)  eventoRow.get(0).get("titulo");
        Integer capacidad = (Integer) eventoRow.get(0).get("capacidad");
        String estado    = (String)  eventoRow.get(0).get("estado");

        String sql = "SELECT a.id, a.evento_id, a.miembro_id, "
                + "m.nombres, m.apellidos, "
                + "a.visitante_nombre, a.visitante_telefono, "
                + "a.presente, a.observacion, a.created_at "
                + "FROM " + tenant + ".asistencias a "
                + "LEFT JOIN " + tenant + ".miembros m ON a.miembro_id = m.id "
                + "WHERE a.evento_id = ? "
                + "ORDER BY a.created_at ASC";

        List<AsistenciaResponse> asistencias = jdbc.query(sql, this::mapRow, eventoId);
        int totalPresentes = (int) asistencias.stream().filter(a -> a.presente()).count();

        return new CheckInListResponse(asistencias, totalPresentes, capacidad, titulo, estado);
    }

    // ── Registrar asistencia (miembro o visitante) ────────────────────────────
    public AsistenciaResponse registrar(UUID eventoId, RegistrarAsistenciaRequest req) {
        String tenant = tenant();

        validarEventoAbierto(tenant, eventoId);
        validarRequest(req);

        if (req.miembroId() != null) {
            validarMiembroExiste(tenant, req.miembroId());
        }

        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO " + tenant + ".asistencias "
                    + "(id, sede_id, evento_id, miembro_id, visitante_nombre, "
                    + " visitante_telefono, presente, observacion) "
                    + "SELECT ?, s.id, ?, ?, ?, ?, TRUE, ? "
                    + "FROM shared.sedes s "
                    + "WHERE s.schema_name = ?",
                    id, eventoId, req.miembroId(),
                    req.visitanteNombre(), req.visitanteTelefono(),
                    req.observacion(), tenant);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("El miembro ya está registrado en este evento");
        }

        log.info("[Asistencias] Registrado en evento={} miembro={} visitante={}",
                eventoId, req.miembroId(), req.visitanteNombre());

        return obtenerAsistencia(tenant, id);
    }

    // ── Eliminar registro ─────────────────────────────────────────────────────
    public void eliminar(UUID eventoId, UUID asistenciaId) {
        String tenant = tenant();

        int filas = jdbc.update(
                "DELETE FROM " + tenant + ".asistencias WHERE id = ? AND evento_id = ?",
                asistenciaId, eventoId);

        if (filas == 0) {
            throw new IllegalArgumentException("Registro de asistencia no encontrado: " + asistenciaId);
        }
        log.info("[Asistencias] Eliminado: {} del evento {}", asistenciaId, eventoId);
    }

    // ── Buscar miembros para check-in ─────────────────────────────────────────
    public List<MiembroCheckInResponse> buscarMiembros(UUID eventoId, String q) {
        String tenant = tenant();
        String like = "%" + q.toLowerCase() + "%";

        String sql = "SELECT m.id, m.nombres, m.apellidos, m.telefono, "
                + "EXISTS(SELECT 1 FROM " + tenant + ".asistencias a "
                + "       WHERE a.evento_id = ? AND a.miembro_id = m.id) AS ya_registrado "
                + "FROM " + tenant + ".miembros m "
                + "WHERE m.deleted_at IS NULL "
                + "AND (LOWER(m.nombres || ' ' || m.apellidos) LIKE ? OR m.telefono LIKE ?) "
                + "ORDER BY m.apellidos, m.nombres "
                + "LIMIT 20";

        return jdbc.query(sql, (rs, i) -> new MiembroCheckInResponse(
                rs.getObject("id", UUID.class),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("telefono"),
                rs.getBoolean("ya_registrado")
        ), eventoId, like, like);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validarEventoAbierto(String tenant, UUID eventoId) {
        String estado = jdbc.queryForObject(
                "SELECT estado FROM " + tenant + ".eventos WHERE id = ? AND deleted_at IS NULL",
                String.class, eventoId);
        if (estado == null) {
            throw new IllegalArgumentException("Evento no encontrado: " + eventoId);
        }
        if (!"ABIERTO".equals(estado)) {
            throw new IllegalArgumentException(
                    "El evento debe estar en estado ABIERTO para registrar asistencia. Estado actual: " + estado);
        }
    }

    private void validarRequest(RegistrarAsistenciaRequest req) {
        if (req.miembroId() == null && (req.visitanteNombre() == null || req.visitanteNombre().isBlank())) {
            throw new IllegalArgumentException(
                    "Debe indicar miembroId o visitanteNombre para registrar la asistencia");
        }
    }

    private void validarMiembroExiste(String tenant, UUID miembroId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros WHERE id = ? AND deleted_at IS NULL",
                Integer.class, miembroId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Miembro no encontrado: " + miembroId);
        }
    }

    private AsistenciaResponse obtenerAsistencia(String tenant, UUID id) {
        return jdbc.query(
                "SELECT a.id, a.evento_id, a.miembro_id, "
                + "m.nombres, m.apellidos, "
                + "a.visitante_nombre, a.visitante_telefono, "
                + "a.presente, a.observacion, a.created_at "
                + "FROM " + tenant + ".asistencias a "
                + "LEFT JOIN " + tenant + ".miembros m ON a.miembro_id = m.id "
                + "WHERE a.id = ?",
                this::mapRow, id)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Error al recuperar asistencia"));
    }

    private AsistenciaResponse mapRow(ResultSet rs, int i) throws SQLException {
        Timestamp cat = rs.getTimestamp("created_at");
        return new AsistenciaResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("evento_id", UUID.class),
                rs.getObject("miembro_id", UUID.class),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("visitante_nombre"),
                rs.getString("visitante_telefono"),
                rs.getBoolean("presente"),
                rs.getString("observacion"),
                cat != null ? cat.toInstant() : null);
    }

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }
        return t;
    }
}
