package com.miltonbass.sgi_backend.celulas.service;

import com.miltonbass.sgi_backend.celulas.dto.SesionGrupoDtos.*;
import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.grupos.service.GrupoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class SesionGrupoService {

    private static final Logger log = LoggerFactory.getLogger(SesionGrupoService.class);

    private final JdbcTemplate jdbc;
    private final GrupoService grupoService;

    public SesionGrupoService(JdbcTemplate jdbc, GrupoService grupoService) {
        this.jdbc = jdbc;
        this.grupoService = grupoService;
    }

    // ── Listar sesiones ───────────────────────────────────────────────────────
    public List<SesionResponse> listar(UUID grupoId, UUID usuarioId) {
        String tenant = tenant();
        if (usuarioId != null) grupoService.validarLiderOSubArbol(tenant, grupoId, usuarioId);

        String sql = "SELECT s.id, s.grupo_id, g.nombre AS grupo_nombre, s.fecha, s.lugar, s.tema, "
                + "s.comentarios, s.ofrenda_monto, s.creado_por, s.created_at, s.updated_at, "
                + "(SELECT COUNT(*) FROM " + tenant + ".asistencias_sesion a "
                + " WHERE a.sesion_id = s.id AND a.presente = TRUE) AS total_presentes, "
                + "(SELECT COUNT(*) FROM " + tenant + ".miembro_grupos mg WHERE mg.grupo_id = s.grupo_id) AS total_miembros "
                + "FROM " + tenant + ".sesiones_grupo s "
                + "JOIN " + tenant + ".grupos g ON g.id = s.grupo_id "
                + "WHERE s.grupo_id = ? "
                + "ORDER BY s.fecha DESC, s.created_at DESC";

        return jdbc.query(sql, this::mapSesion, grupoId);
    }

    // ── Crear sesión ──────────────────────────────────────────────────────────
    public SesionResponse crear(UUID grupoId, CreateSesionRequest req, UUID creadoPor, UUID usuarioId) {
        String tenant = tenant();
        validarAcceso(tenant, grupoId, usuarioId);

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO " + tenant + ".sesiones_grupo "
                + "(id, grupo_id, fecha, lugar, tema, comentarios, ofrenda_monto, creado_por, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, grupoId,
                Date.valueOf(req.fecha()),
                req.lugar(), req.tema(), req.comentarios(),
                req.ofrendaMonto(), creadoPor);

        log.info("[Sesiones] Creada en grupo={} fecha={}", grupoId, req.fecha());
        return obtenerInterno(tenant, grupoId, id);
    }

    // ── Obtener sesión (lectura: propio o sub-árbol) ──────────────────────────
    public SesionResponse obtener(UUID grupoId, UUID sesionId, UUID usuarioId) {
        String tenant = tenant();
        if (usuarioId != null) grupoService.validarLiderOSubArbol(tenant, grupoId, usuarioId);
        return obtenerInterno(tenant, grupoId, sesionId);
    }

    // ── Actualizar sesión ─────────────────────────────────────────────────────
    public SesionResponse actualizar(UUID grupoId, UUID sesionId, UpdateSesionRequest req, UUID usuarioId) {
        String tenant = tenant();
        validarAcceso(tenant, grupoId, usuarioId);
        SesionResponse actual = obtenerInterno(tenant, grupoId, sesionId);

        LocalDate fecha        = req.fecha()        != null ? req.fecha()        : actual.fecha();
        String lugar           = req.lugar()        != null ? req.lugar()        : actual.lugar();
        String tema            = req.tema()         != null ? req.tema()         : actual.tema();
        String comentarios     = req.comentarios()  != null ? req.comentarios()  : actual.comentarios();
        BigDecimal ofrenda     = req.ofrendaMonto() != null ? req.ofrendaMonto() : actual.ofrendaMonto();

        jdbc.update(
                "UPDATE " + tenant + ".sesiones_grupo "
                + "SET fecha=?, lugar=?, tema=?, comentarios=?, ofrenda_monto=? WHERE id=? AND grupo_id=?",
                Date.valueOf(fecha), lugar, tema, comentarios, ofrenda, sesionId, grupoId);

        log.info("[Sesiones] Actualizada: {}", sesionId);
        return obtenerInterno(tenant, grupoId, sesionId);
    }

    // ── Eliminar sesión ───────────────────────────────────────────────────────
    public void eliminar(UUID grupoId, UUID sesionId) {
        String tenant = tenant();
        int rows = jdbc.update(
                "DELETE FROM " + tenant + ".sesiones_grupo WHERE id = ? AND grupo_id = ?",
                sesionId, grupoId);
        if (rows == 0) {
            throw new IllegalArgumentException("Sesión no encontrada: " + sesionId);
        }
        log.info("[Sesiones] Eliminada: {}", sesionId);
    }

    // ── Listar asistencias de una sesión ──────────────────────────────────────
    public AsistenciaSesionListResponse listarAsistencias(UUID grupoId, UUID sesionId, UUID usuarioId) {
        String tenant = tenant();
        if (usuarioId != null) grupoService.validarLiderOSubArbol(tenant, grupoId, usuarioId);
        SesionResponse sesion = obtenerInterno(tenant, grupoId, sesionId);

        String sql = "SELECT a.id, a.sesion_id, a.miembro_id, "
                + "m.nombres, m.apellidos, "
                + "a.visitante_nombre, a.visitante_telefono, "
                + "a.presente, a.created_at "
                + "FROM " + tenant + ".asistencias_sesion a "
                + "LEFT JOIN " + tenant + ".miembros m ON a.miembro_id = m.id "
                + "WHERE a.sesion_id = ? "
                + "ORDER BY a.created_at ASC";

        List<AsistenciaSesionResponse> lista = jdbc.query(sql, this::mapAsistencia, sesionId);
        int totalPresentes = (int) lista.stream().filter(a -> a.presente()).count();

        return new AsistenciaSesionListResponse(lista, totalPresentes, sesion.totalMiembros(),
                sesion.fecha(), sesion.grupoNombre());
    }

    // ── Registrar asistencia en sesión ────────────────────────────────────────
    public AsistenciaSesionResponse registrarAsistencia(UUID grupoId, UUID sesionId,
                                                         RegistrarAsistenciaSesionRequest req,
                                                         UUID usuarioId) {
        String tenant = tenant();
        validarAcceso(tenant, grupoId, usuarioId);
        obtenerInterno(tenant, grupoId, sesionId); // valida que la sesión existe

        if (req.miembroId() == null && (req.visitanteNombre() == null || req.visitanteNombre().isBlank())) {
            throw new IllegalArgumentException("Debe indicar miembroId o visitanteNombre");
        }

        if (req.miembroId() != null) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + tenant + ".miembros WHERE id = ? AND deleted_at IS NULL",
                    Integer.class, req.miembroId());
            if (count == null || count == 0) {
                throw new IllegalArgumentException("Miembro no encontrado: " + req.miembroId());
            }
        }

        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO " + tenant + ".asistencias_sesion "
                    + "(id, sesion_id, miembro_id, visitante_nombre, visitante_telefono, presente, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, TRUE, NOW())",
                    id, sesionId, req.miembroId(), req.visitanteNombre(), req.visitanteTelefono());
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("El miembro ya está registrado en esta sesión");
        }

        log.info("[Sesiones] Asistencia registrada en sesion={} miembro={} visitante={}",
                sesionId, req.miembroId(), req.visitanteNombre());
        return obtenerAsistencia(tenant, id);
    }

    // ── Eliminar asistencia ───────────────────────────────────────────────────
    public void eliminarAsistencia(UUID grupoId, UUID sesionId, UUID asistenciaId, UUID usuarioId) {
        String tenant = tenant();
        validarAcceso(tenant, grupoId, usuarioId);

        int rows = jdbc.update(
                "DELETE FROM " + tenant + ".asistencias_sesion a "
                + "USING " + tenant + ".sesiones_grupo s "
                + "WHERE a.id = ? AND a.sesion_id = s.id AND s.grupo_id = ?",
                asistenciaId, grupoId);

        if (rows == 0) {
            throw new IllegalArgumentException("Registro de asistencia no encontrado: " + asistenciaId);
        }
        log.info("[Sesiones] Asistencia eliminada: {}", asistenciaId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SesionResponse obtenerInterno(String tenant, UUID grupoId, UUID sesionId) {
        String sql = "SELECT s.id, s.grupo_id, g.nombre AS grupo_nombre, s.fecha, s.lugar, s.tema, "
                + "s.comentarios, s.ofrenda_monto, s.creado_por, s.created_at, s.updated_at, "
                + "(SELECT COUNT(*) FROM " + tenant + ".asistencias_sesion a "
                + " WHERE a.sesion_id = s.id AND a.presente = TRUE) AS total_presentes, "
                + "(SELECT COUNT(*) FROM " + tenant + ".miembro_grupos mg WHERE mg.grupo_id = s.grupo_id) AS total_miembros "
                + "FROM " + tenant + ".sesiones_grupo s "
                + "JOIN " + tenant + ".grupos g ON g.id = s.grupo_id "
                + "WHERE s.id = ? AND s.grupo_id = ?";
        return jdbc.query(sql, this::mapSesion, sesionId, grupoId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada: " + sesionId));
    }

    private void validarAcceso(String tenant, UUID grupoId, UUID usuarioId) {
        if (usuarioId != null) {
            grupoService.validarLiderDelGrupo(tenant, grupoId, usuarioId);
        }
    }

    private AsistenciaSesionResponse obtenerAsistencia(String tenant, UUID id) {
        return jdbc.query(
                "SELECT a.id, a.sesion_id, a.miembro_id, m.nombres, m.apellidos, "
                + "a.visitante_nombre, a.visitante_telefono, a.presente, a.created_at "
                + "FROM " + tenant + ".asistencias_sesion a "
                + "LEFT JOIN " + tenant + ".miembros m ON a.miembro_id = m.id "
                + "WHERE a.id = ?",
                this::mapAsistencia, id)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Error al recuperar asistencia"));
    }

    private SesionResponse mapSesion(ResultSet rs, int i) throws SQLException {
        Timestamp cat = rs.getTimestamp("created_at");
        Timestamp uat = rs.getTimestamp("updated_at");
        Date fecha = rs.getDate("fecha");
        Object ofrendaObj = rs.getObject("ofrenda_monto");
        return new SesionResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("grupo_id", UUID.class),
                rs.getString("grupo_nombre"),
                fecha != null ? fecha.toLocalDate() : null,
                rs.getString("lugar"),
                rs.getString("tema"),
                rs.getString("comentarios"),
                ofrendaObj != null ? new BigDecimal(ofrendaObj.toString()) : null,
                rs.getObject("creado_por", UUID.class),
                cat != null ? cat.toInstant() : null,
                uat != null ? uat.toInstant() : null,
                rs.getInt("total_presentes"),
                rs.getInt("total_miembros"));
    }

    private AsistenciaSesionResponse mapAsistencia(ResultSet rs, int i) throws SQLException {
        Timestamp cat = rs.getTimestamp("created_at");
        return new AsistenciaSesionResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("sesion_id", UUID.class),
                rs.getObject("miembro_id", UUID.class),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("visitante_nombre"),
                rs.getString("visitante_telefono"),
                rs.getBoolean("presente"),
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
