package com.miltonbass.sgi_backend.grupos.service;

import com.miltonbass.sgi_backend.config.TenantContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GrupoService {

    private static final Logger log = LoggerFactory.getLogger(GrupoService.class);

    private final JdbcTemplate jdbc;

    public GrupoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Listar ────────────────────────────────────────────────────────────────
    // usuarioId != null → filtrar solo grupos donde ese usuario es LIDER (LIDER_CELULA)
    public GrupoPageResponse listar(Boolean activo, String tipo, int page, int size, UUID usuarioId) {
        String tenant = tenant();
        List<Object> params = new ArrayList<>();

        StringBuilder where = new StringBuilder();
        if (activo != null) {
            where.append("WHERE g.activo = ?");
            params.add(activo);
        }
        if (tipo != null && !tipo.isBlank()) {
            String op = where.length() == 0 ? "WHERE " : " AND ";
            where.append(op).append("g.tipo = ?");
            params.add(tipo.toUpperCase());
        }
        if (usuarioId != null) {
            String op = where.length() == 0 ? "WHERE " : " AND ";
            where.append(op).append("g.id IN ("
                    + "SELECT mg.grupo_id FROM ").append(tenant).append(".miembro_grupos mg "
                    + "JOIN ").append(tenant).append(".miembros m ON m.id = mg.miembro_id "
                    + "WHERE m.usuario_id = ? AND mg.rol = 'LIDER')");
            params.add(usuarioId);
        }

        String countSql = "SELECT COUNT(*) FROM " + tenant + ".grupos g " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        params.add(size);
        params.add((long) page * size);

        String sql = "SELECT g.id, g.sede_id, g.nombre, g.tipo, g.lider_id, g.grupo_padre_id, "
                + "  CASE WHEN g.lider_id IS NOT NULL THEN "
                + "    (SELECT m.nombres || ' ' || m.apellidos FROM " + tenant + ".miembros m WHERE m.id = g.lider_id) "
                + "  END AS lider_nombre, "
                + "  g.descripcion, g.lugar, g.activo, g.created_at, g.updated_at, "
                + "  (SELECT COUNT(*) FROM " + tenant + ".miembro_grupos mg WHERE mg.grupo_id = g.id) AS total_miembros "
                + "FROM " + tenant + ".grupos g "
                + where
                + " ORDER BY g.nombre ASC LIMIT ? OFFSET ?";

        List<GrupoResponse> content = jdbc.query(sql, (rs, rowNum) -> mapGrupo(rs), params.toArray());

        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new GrupoPageResponse(content, page, size, total, totalPages);
    }

    // ── Obtener ───────────────────────────────────────────────────────────────
    public GrupoResponse obtener(UUID id) {
        String tenant = tenant();
        String sql = "SELECT g.id, g.sede_id, g.nombre, g.tipo, g.lider_id, g.grupo_padre_id, "
                + "  CASE WHEN g.lider_id IS NOT NULL THEN "
                + "    (SELECT m.nombres || ' ' || m.apellidos FROM " + tenant + ".miembros m WHERE m.id = g.lider_id) "
                + "  END AS lider_nombre, "
                + "  g.descripcion, g.lugar, g.activo, g.created_at, g.updated_at, "
                + "  (SELECT COUNT(*) FROM " + tenant + ".miembro_grupos mg WHERE mg.grupo_id = g.id) AS total_miembros "
                + "FROM " + tenant + ".grupos g WHERE g.id = ?";

        return jdbc.query(sql, (rs, rowNum) -> mapGrupo(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado: " + id));
    }

    // ── Obtener con validación de liderazgo ───────────────────────────────────
    public GrupoResponse obtenerConAcceso(UUID id, UUID usuarioId) {
        GrupoResponse grupo = obtener(id);
        if (usuarioId != null) {
            validarLiderDelGrupo(tenant(), id, usuarioId);
        }
        return grupo;
    }

    // ── Crear ─────────────────────────────────────────────────────────────────
    // pendienteAprobacion=true → activo=false (creado por LIDER_CELULA, requiere aprobación)
    public GrupoResponse crear(CreateGrupoRequest req, UUID sedeId, boolean pendienteAprobacion) {
        String tenant = tenant();

        if (req.liderId() != null) {
            validarMiembroExiste(tenant, req.liderId(), "lider");
        }

        UUID id = UUID.randomUUID();
        Instant ahora = Instant.now();
        boolean activo = !pendienteAprobacion;

        jdbc.update(
                "INSERT INTO " + tenant + ".grupos "
                + "(id, sede_id, nombre, tipo, lider_id, grupo_padre_id, descripcion, lugar, activo, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, sedeId, req.nombre(), req.tipo() != null ? req.tipo() : "CELULA",
                req.liderId(), req.grupoPadreId(), req.descripcion(), req.lugar(),
                activo, Timestamp.from(ahora), Timestamp.from(ahora));

        // Registrar líder en miembro_grupos para que /mi-arbol lo reconozca
        if (req.liderId() != null) {
            jdbc.update(
                    "INSERT INTO " + tenant + ".miembro_grupos (id, grupo_id, miembro_id, rol, fecha_ingreso, created_at) "
                    + "VALUES (?, ?, ?, 'LIDER', CURRENT_DATE, NOW()) "
                    + "ON CONFLICT (grupo_id, miembro_id) DO UPDATE SET rol = 'LIDER'",
                    UUID.randomUUID(), id, req.liderId());
        }

        log.info("[Grupos] Creado: {} tipo={} | sede={} | activo={}", req.nombre(), req.tipo(), sedeId, activo);
        return obtener(id);
    }

    // ── Activar (aprobar grupo pendiente) ─────────────────────────────────────
    public void activar(UUID id) {
        String tenant = tenant();
        int rows = jdbc.update("UPDATE " + tenant + ".grupos SET activo = TRUE WHERE id = ?", id);
        if (rows == 0) {
            throw new IllegalArgumentException("Grupo no encontrado: " + id);
        }
        log.info("[Grupos] Activado: {}", id);
    }

    // ── Actualizar (admin/pastor: todos los campos; lider: solo nombre/descripcion/lugar) ──
    public GrupoResponse actualizar(UUID id, UpdateGrupoRequest req, UUID usuarioId) {
        String tenant = tenant();
        GrupoResponse existing = obtener(id);

        if (usuarioId != null) {
            // LIDER_CELULA: validar que es su grupo y limitar campos editables
            validarLiderDelGrupo(tenant, id, usuarioId);
            String nombre      = req.nombre()      != null ? req.nombre()      : existing.nombre();
            String descripcion = req.descripcion() != null ? req.descripcion() : existing.descripcion();
            String lugar       = req.lugar()       != null ? req.lugar()       : existing.lugar();
            jdbc.update(
                    "UPDATE " + tenant + ".grupos SET nombre = ?, descripcion = ?, lugar = ? WHERE id = ?",
                    nombre, descripcion, lugar, id);
            log.info("[Grupos] Actualizado por lider {}: {}", usuarioId, id);
            return obtener(id);
        }

        // Admin/Pastor: todos los campos
        if (req.liderId() != null) {
            validarMiembroExiste(tenant, req.liderId(), "lider");
        }

        String nombre        = req.nombre()        != null ? req.nombre()        : existing.nombre();
        String tipo          = req.tipo()          != null ? req.tipo()          : existing.tipo();
        String descripcion   = req.descripcion()   != null ? req.descripcion()   : existing.descripcion();
        String lugar         = req.lugar()         != null ? req.lugar()         : existing.lugar();
        boolean activo       = req.activo()        != null ? req.activo()        : existing.activo();
        UUID liderId         = req.liderId()       != null ? req.liderId()       : existing.liderId();
        UUID grupoPadreId    = req.grupoPadreId()  != null ? req.grupoPadreId()  : existing.grupoPadreId();

        jdbc.update(
                "UPDATE " + tenant + ".grupos SET nombre=?, tipo=?, lider_id=?, grupo_padre_id=?, "
                + "descripcion=?, lugar=?, activo=? WHERE id=?",
                nombre, tipo, liderId, grupoPadreId, descripcion, lugar, activo, id);

        // Sincronizar miembro_grupos cuando hay líder asignado
        if (liderId != null) {
            jdbc.update(
                    "INSERT INTO " + tenant + ".miembro_grupos (id, grupo_id, miembro_id, rol, fecha_ingreso, created_at) "
                    + "VALUES (?, ?, ?, 'LIDER', CURRENT_DATE, NOW()) "
                    + "ON CONFLICT (grupo_id, miembro_id) DO UPDATE SET rol = 'LIDER'",
                    UUID.randomUUID(), id, liderId);
        }

        log.info("[Grupos] Actualizado: {}", id);
        return obtener(id);
    }

    // ── Desactivar ────────────────────────────────────────────────────────────
    public void desactivar(UUID id) {
        String tenant = tenant();
        obtener(id);
        jdbc.update("UPDATE " + tenant + ".grupos SET activo = FALSE WHERE id = ?", id);
        log.info("[Grupos] Desactivado: {}", id);
    }

    // ── Asignar miembro al grupo ──────────────────────────────────────────────
    public MiembroGrupoResponse asignarMiembro(UUID grupoId, AsignarMiembroRequest req) {
        String tenant = tenant();

        obtener(grupoId);
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

    // ── Listar miembros del grupo ─────────────────────────────────────────────
    public GrupoMiembrosResponse listarMiembros(UUID grupoId, UUID usuarioId) {
        String tenant = tenant();
        if (usuarioId != null) {
            validarLiderDelGrupo(tenant, grupoId, usuarioId);
        }
        GrupoResponse grupo = obtener(grupoId);
        List<MiembroGrupoResponse> miembros = listarMiembrosInterno(tenant, grupoId);
        return new GrupoMiembrosResponse(grupoId, grupo.nombre(), grupo.tipo(), miembros);
    }

    // ── Remover miembro del grupo ─────────────────────────────────────────────
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

    // ── Validar que el usuario es lider del grupo ─────────────────────────────
    public void validarLiderDelGrupo(String tenant, UUID grupoId, UUID usuarioId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembro_grupos mg "
                + "JOIN " + tenant + ".miembros m ON m.id = mg.miembro_id "
                + "WHERE mg.grupo_id = ? AND m.usuario_id = ? AND mg.rol = 'LIDER'",
                Integer.class, grupoId, usuarioId);
        if (count == null || count == 0) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No eres el lider de este grupo");
        }
    }

    // ── Validar que el grupo es propio o descendiente del árbol del líder ─────
    public void validarLiderOSubArbol(String tenant, UUID grupoId, UUID usuarioId) {
        Integer count = jdbc.queryForObject(
                "WITH RECURSIVE sub AS ("
                + "  SELECT g.id FROM " + tenant + ".grupos g "
                + "  JOIN " + tenant + ".miembro_grupos mg ON mg.grupo_id = g.id "
                + "  JOIN " + tenant + ".miembros m ON m.id = mg.miembro_id "
                + "  WHERE m.usuario_id = ? AND mg.rol = 'LIDER' "
                + "  UNION ALL "
                + "  SELECT g.id FROM " + tenant + ".grupos g "
                + "  JOIN sub ON g.grupo_padre_id = sub.id "
                + ") "
                + "SELECT COUNT(*) FROM sub WHERE id = ?",
                Integer.class, usuarioId, grupoId);
        if (count == null || count == 0) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tienes acceso a este grupo");
        }
    }

    // ── Sub-árbol del líder (H6.2) ────────────────────────────────────────────
    public GrupoArbolResponse obtenerSubArbol(UUID usuarioId) {
        String tenant = tenant();

        // Grupo raíz donde el usuario es LIDER
        List<UUID> raizIds = jdbc.query(
                "SELECT mg.grupo_id FROM " + tenant + ".miembro_grupos mg "
                + "JOIN " + tenant + ".miembros m ON m.id = mg.miembro_id "
                + "WHERE m.usuario_id = ? AND mg.rol = 'LIDER'",
                (rs, i) -> rs.getObject("grupo_id", UUID.class), usuarioId);

        if (raizIds.isEmpty()) {
            throw new IllegalArgumentException("No tienes ningún grupo asignado como líder");
        }

        UUID raizId = raizIds.get(0);
        GrupoResponse raiz = obtener(raizId);

        String sql = "WITH RECURSIVE sub AS ("
                + "  SELECT g.id, g.nombre, g.tipo, g.lider_id, g.grupo_padre_id, 1 AS nivel "
                + "  FROM " + tenant + ".grupos g "
                + "  WHERE g.grupo_padre_id = ? AND g.activo = TRUE "
                + "  UNION ALL "
                + "  SELECT g.id, g.nombre, g.tipo, g.lider_id, g.grupo_padre_id, sub.nivel + 1 "
                + "  FROM " + tenant + ".grupos g "
                + "  JOIN sub ON g.grupo_padre_id = sub.id "
                + "  WHERE g.activo = TRUE "
                + ") "
                + "SELECT sub.id, sub.nombre, sub.tipo, sub.lider_id, sub.grupo_padre_id, sub.nivel, "
                + "  (SELECT m.nombres || ' ' || m.apellidos FROM " + tenant + ".miembros m "
                + "   WHERE m.id = sub.lider_id) AS lider_nombre, "
                + "  (SELECT COUNT(*) FROM " + tenant + ".miembro_grupos mg "
                + "   WHERE mg.grupo_id = sub.id) AS total_miembros, "
                + "  (SELECT MAX(sg.fecha) FROM " + tenant + ".sesiones_grupo sg "
                + "   WHERE sg.grupo_id = sub.id) AS ultima_sesion_fecha, "
                + "  (SELECT AVG(cnt) FROM ("
                + "     SELECT COUNT(*) AS cnt FROM " + tenant + ".asistencias_sesion a "
                + "     JOIN " + tenant + ".sesiones_grupo sg ON sg.id = a.sesion_id "
                + "     WHERE sg.grupo_id = sub.id AND a.presente = TRUE "
                + "       AND sg.fecha >= CURRENT_DATE - INTERVAL '30 days' "
                + "     GROUP BY sg.id"
                + "  ) t) AS promedio_asistencia_30d "
                + "FROM sub "
                + "ORDER BY sub.nivel ASC, sub.nombre ASC";

        List<GrupoHijoResumen> hijos = jdbc.query(sql, (rs, rowNum) -> {
            java.sql.Date ultimaFecha = rs.getDate("ultima_sesion_fecha");
            Object prom = rs.getObject("promedio_asistencia_30d");
            return new GrupoHijoResumen(
                    rs.getObject("id", UUID.class),
                    rs.getString("nombre"),
                    rs.getString("tipo"),
                    rs.getObject("grupo_padre_id", UUID.class),
                    rs.getString("lider_nombre"),
                    rs.getInt("nivel"),
                    rs.getInt("total_miembros"),
                    ultimaFecha != null ? ultimaFecha.toLocalDate() : null,
                    prom != null ? ((Number) prom).doubleValue() : null);
        }, raizId);

        return new GrupoArbolResponse(raizId, raiz.nombre(), hijos);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
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
        Object padreIdObj = rs.getObject("grupo_padre_id");
        return new GrupoResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("sede_id", UUID.class),
                rs.getString("nombre"),
                rs.getString("tipo"),
                liderIdObj != null ? UUID.fromString(liderIdObj.toString()) : null,
                rs.getString("lider_nombre"),
                padreIdObj != null ? UUID.fromString(padreIdObj.toString()) : null,
                rs.getString("descripcion"),
                rs.getString("lugar"),
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
