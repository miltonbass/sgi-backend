package com.miltonbass.sgi_backend.grupos.service;

import com.miltonbass.sgi_backend.grupos.dto.GrupoDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class GrupoService {

    private static final Logger log = LoggerFactory.getLogger(GrupoService.class);

    private final JdbcTemplate jdbc;

    public GrupoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Listar ────────────────────────────────────────────────────────
    public GrupoPageResponse listar(Boolean activo, int page, int size) {
        String tenant = tenant();

        String whereClause = activo != null
                ? "WHERE g.activo = " + activo
                : "";

        String countSql = "SELECT COUNT(*) FROM " + tenant + ".grupos g " + whereClause;
        Long total = jdbc.queryForObject(countSql, Long.class);
        if (total == null) total = 0L;

        String sql = "SELECT g.id, g.sede_id, g.nombre, g.tipo, g.lider_id, "
                + "  CASE WHEN g.lider_id IS NOT NULL THEN "
                + "    (SELECT m.nombres || ' ' || m.apellidos FROM " + tenant + ".miembros m WHERE m.id = g.lider_id) "
                + "  END AS lider_nombre, "
                + "  g.descripcion, g.activo, g.created_at, g.updated_at, "
                + "  (SELECT COUNT(*) FROM " + tenant + ".miembro_grupos mg WHERE mg.grupo_id = g.id) AS total_miembros "
                + "FROM " + tenant + ".grupos g "
                + whereClause
                + " ORDER BY g.nombre ASC "
                + " LIMIT ? OFFSET ?";

        List<GrupoResponse> content = jdbc.query(sql,
                (rs, rowNum) -> mapGrupo(rs),
                size, (long) page * size);

        int totalPages = (int) Math.ceil((double) total / size);
        return new GrupoPageResponse(content, page, size, total, totalPages);
    }

    // ── Obtener ───────────────────────────────────────────────────────
    public GrupoResponse obtener(UUID id) {
        String tenant = tenant();
        String sql = "SELECT g.id, g.sede_id, g.nombre, g.tipo, g.lider_id, "
                + "  CASE WHEN g.lider_id IS NOT NULL THEN "
                + "    (SELECT m.nombres || ' ' || m.apellidos FROM " + tenant + ".miembros m WHERE m.id = g.lider_id) "
                + "  END AS lider_nombre, "
                + "  g.descripcion, g.activo, g.created_at, g.updated_at, "
                + "  (SELECT COUNT(*) FROM " + tenant + ".miembro_grupos mg WHERE mg.grupo_id = g.id) AS total_miembros "
                + "FROM " + tenant + ".grupos g WHERE g.id = ?";

        return jdbc.query(sql, (rs, rowNum) -> mapGrupo(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado: " + id));
    }

    // ── Crear ─────────────────────────────────────────────────────────
    public GrupoResponse crear(CreateGrupoRequest req, UUID sedeId) {
        String tenant = tenant();

        if (req.liderId() != null) {
            validarMiembroExiste(tenant, req.liderId(), "lider");
        }

        UUID id = UUID.randomUUID();
        Instant ahora = Instant.now();

        jdbc.update(
                "INSERT INTO " + tenant + ".grupos (id, sede_id, nombre, tipo, lider_id, descripcion, activo, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)",
                id, sedeId, req.nombre(), req.tipo() != null ? req.tipo() : "CELULA",
                req.liderId(), req.descripcion(),
                Timestamp.from(ahora), Timestamp.from(ahora));

        log.info("[Grupos] Creado: {} tipo={} | sede={}", req.nombre(), req.tipo(), sedeId);
        return obtener(id);
    }

    // ── Actualizar ────────────────────────────────────────────────────
    public GrupoResponse actualizar(UUID id, UpdateGrupoRequest req) {
        String tenant = tenant();

        GrupoResponse existing = obtener(id);

        if (req.liderId() != null) {
            validarMiembroExiste(tenant, req.liderId(), "lider");
        }

        String nombre = req.nombre() != null ? req.nombre() : existing.nombre();
        String tipo = req.tipo() != null ? req.tipo() : existing.tipo();
        String descripcion = req.descripcion() != null ? req.descripcion() : existing.descripcion();
        boolean activo = req.activo() != null ? req.activo() : existing.activo();
        // liderId: null en request significa "no cambiar"; para quitar hay que enviar un flag especial
        // En este diseño, si liderId es null en el request se mantiene el existente
        UUID liderId = req.liderId() != null ? req.liderId() : existing.liderId();

        jdbc.update(
                "UPDATE " + tenant + ".grupos SET nombre = ?, tipo = ?, lider_id = ?, descripcion = ?, activo = ? WHERE id = ?",
                nombre, tipo, liderId, descripcion, activo, id);

        log.info("[Grupos] Actualizado: {}", id);
        return obtener(id);
    }

    // ── Desactivar ────────────────────────────────────────────────────
    public void desactivar(UUID id) {
        String tenant = tenant();
        obtener(id); // valida que existe
        jdbc.update("UPDATE " + tenant + ".grupos SET activo = FALSE WHERE id = ?", id);
        log.info("[Grupos] Desactivado: {}", id);
    }

    // ── Asignar miembro al grupo ───────────────────────────────────────
    public MiembroGrupoResponse asignarMiembro(UUID grupoId, AsignarMiembroRequest req) {
        String tenant = tenant();

        obtener(grupoId); // valida que el grupo existe
        validarMiembroExiste(tenant, req.miembroId(), "miembro");

        Long exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembro_grupos WHERE grupo_id = ? AND miembro_id = ?",
                Long.class, grupoId, req.miembroId());
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("El miembro ya está asignado a este grupo");
        }

        String rol = req.rol() != null ? req.rol() : "PARTICIPANTE";
        LocalDate fechaIngreso = req.fechaIngreso() != null ? req.fechaIngreso() : LocalDate.now();
        UUID id = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO " + tenant + ".miembro_grupos (id, grupo_id, miembro_id, rol, fecha_ingreso, created_at) "
                + "VALUES (?, ?, ?, ?, ?, NOW())",
                id, grupoId, req.miembroId(), rol, Date.valueOf(fechaIngreso));

        log.info("[Grupos] Miembro {} asignado a grupo {} con rol {}", req.miembroId(), grupoId, rol);

        return listarMiembrosInterno(tenant, grupoId).stream()
                .filter(m -> m.miembroId().equals(req.miembroId()))
                .findFirst()
                .orElseThrow();
    }

    // ── Listar miembros del grupo ──────────────────────────────────────
    public GrupoMiembrosResponse listarMiembros(UUID grupoId) {
        String tenant = tenant();
        GrupoResponse grupo = obtener(grupoId);
        List<MiembroGrupoResponse> miembros = listarMiembrosInterno(tenant, grupoId);
        return new GrupoMiembrosResponse(grupoId, grupo.nombre(), grupo.tipo(), miembros);
    }

    // ── Remover miembro del grupo ─────────────────────────────────────
    public void removerMiembro(UUID grupoId, UUID miembroId) {
        String tenant = tenant();

        int rows = jdbc.update(
                "DELETE FROM " + tenant + ".miembro_grupos WHERE grupo_id = ? AND miembro_id = ?",
                grupoId, miembroId);

        if (rows == 0) {
            throw new IllegalArgumentException(
                    "El miembro " + miembroId + " no está asignado al grupo " + grupoId);
        }
        log.info("[Grupos] Miembro {} removido de grupo {}", miembroId, grupoId);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String tenant() {
        String t = com.miltonbass.sgi_backend.config.TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }
        return t;
    }

    private void validarMiembroExiste(String tenant, UUID miembroId, String campo) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros WHERE id = ? AND deleted_at IS NULL",
                Long.class, miembroId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("El " + campo + " especificado no existe en esta sede: " + miembroId);
        }
    }

    private List<MiembroGrupoResponse> listarMiembrosInterno(String tenant, UUID grupoId) {
        String sql = "SELECT mg.id, mg.miembro_id, m.nombres, m.apellidos, m.email, m.telefono, "
                + "  m.estado, mg.rol, mg.fecha_ingreso, mg.created_at "
                + "FROM " + tenant + ".miembro_grupos mg "
                + "JOIN " + tenant + ".miembros m ON m.id = mg.miembro_id "
                + "WHERE mg.grupo_id = ? "
                + "ORDER BY mg.rol ASC, m.apellidos ASC, m.nombres ASC";

        return jdbc.query(sql, (rs, rowNum) -> mapMiembroGrupo(rs), grupoId);
    }

    private GrupoResponse mapGrupo(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Object liderIdObj = rs.getObject("lider_id");
        return new GrupoResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("sede_id", UUID.class),
                rs.getString("nombre"),
                rs.getString("tipo"),
                liderIdObj != null ? UUID.fromString(liderIdObj.toString()) : null,
                rs.getString("lider_nombre"),
                rs.getString("descripcion"),
                rs.getBoolean("activo"),
                rs.getInt("total_miembros"),
                createdAt != null ? createdAt.toInstant() : null,
                updatedAt != null ? updatedAt.toInstant() : null);
    }

    private MiembroGrupoResponse mapMiembroGrupo(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Date fechaIngreso = rs.getDate("fecha_ingreso");
        return new MiembroGrupoResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("miembro_id", UUID.class),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("email"),
                rs.getString("telefono"),
                rs.getString("estado"),
                rs.getString("rol"),
                fechaIngreso != null ? fechaIngreso.toLocalDate() : null,
                createdAt != null ? createdAt.toInstant() : null);
    }
}
