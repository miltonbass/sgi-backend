package com.miltonbass.sgi_backend.consolidacion.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.consolidacion.dto.ContactoDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ContactoService {

    private static final Logger log = LoggerFactory.getLogger(ContactoService.class);

    private static final Set<String> ROLES_SUPERIORES =
            Set.of("ADMIN_GLOBAL", "ADMIN_SEDE", "PASTOR_SEDE", "PASTOR_PRINCIPAL");

    private final JdbcTemplate jdbc;

    public ContactoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Registrar contacto ────────────────────────────────────────────────────
    public ContactoResponse registrar(UUID miembroId, UUID usuarioId,
                                      RegistrarContactoRequest req) {
        String tenant = tenant();
        UUID consolidadorMiembroId = buscarMiembroId(tenant, usuarioId);
        validarAsignacion(tenant, miembroId, consolidadorMiembroId);

        UUID id = UUID.randomUUID();
        UUID sedeId = buscarSedeId(tenant);

        jdbc.update(
                "INSERT INTO " + tenant + ".contactos_consolidacion "
                + "(id, sede_id, miembro_id, consolidador_id, fecha, tipo, resumen, "
                + " proxima_accion, fecha_proxima_accion, recordatorio_activo) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, sedeId, miembroId, consolidadorMiembroId,
                req.fecha(), req.tipo(), req.resumen(),
                req.proximaAccion(), req.fechaProximaAccion(),
                req.recordatorioActivo());

        log.info("[Contactos] Registrado tipo={} miembro={} consolidador={}",
                req.tipo(), miembroId, consolidadorMiembroId);

        return new ContactoResponse(id, miembroId, consolidadorMiembroId,
                req.fecha(), req.tipo(), req.resumen(),
                req.proximaAccion(), req.fechaProximaAccion(),
                req.recordatorioActivo(), java.time.Instant.now());
    }

    // ── Listar contactos ──────────────────────────────────────────────────────
    public ContactoPageResponse listar(UUID miembroId, UUID usuarioId,
                                       List<String> roles, int page, int size) {
        String tenant = tenant();
        String filtroConsolidador = filtroConsolidadorSql(tenant, usuarioId, roles);

        String countSql = "SELECT COUNT(*) FROM " + tenant + ".contactos_consolidacion "
                + "WHERE miembro_id = ?" + filtroConsolidador;
        Long total = jdbc.queryForObject(countSql, Long.class, miembroId);
        if (total == null) total = 0L;

        String dataSql = "SELECT id, miembro_id, consolidador_id, fecha, tipo, resumen, "
                + "proxima_accion, fecha_proxima_accion, recordatorio_activo, created_at "
                + "FROM " + tenant + ".contactos_consolidacion "
                + "WHERE miembro_id = ?" + filtroConsolidador
                + " ORDER BY fecha DESC, created_at DESC LIMIT ? OFFSET ?";

        List<ContactoResponse> content = jdbc.query(dataSql, this::mapContacto,
                miembroId, size, (long) page * size);

        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new ContactoPageResponse(content, page, size, total, totalPages);
    }

    // ── Resumen ───────────────────────────────────────────────────────────────
    public ResumenContactosResponse resumen(UUID miembroId, UUID usuarioId,
                                            List<String> roles) {
        String tenant = tenant();
        String filtroConsolidador = filtroConsolidadorSql(tenant, usuarioId, roles);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".contactos_consolidacion "
                + "WHERE miembro_id = ?" + filtroConsolidador,
                Long.class, miembroId);
        int totalContactos = total != null ? total.intValue() : 0;

        if (totalContactos == 0) {
            return new ResumenContactosResponse(0, null, null, null, null);
        }

        // Último contacto + días transcurridos
        List<ContactoResponse> ultimos = jdbc.query(
                "SELECT id, miembro_id, consolidador_id, fecha, tipo, resumen, "
                + "proxima_accion, fecha_proxima_accion, recordatorio_activo, created_at "
                + "FROM " + tenant + ".contactos_consolidacion "
                + "WHERE miembro_id = ?" + filtroConsolidador
                + " ORDER BY fecha DESC, created_at DESC LIMIT 1",
                this::mapContacto, miembroId);

        ContactoResponse ultimo = ultimos.isEmpty() ? null : ultimos.get(0);
        Integer diasDesdeUltimo = null;
        if (ultimo != null && ultimo.fecha() != null) {
            diasDesdeUltimo = (int) (LocalDate.now().toEpochDay() - ultimo.fecha().toEpochDay());
        }

        return new ResumenContactosResponse(
                totalContactos, diasDesdeUltimo, ultimo,
                ultimo != null ? ultimo.proximaAccion() : null,
                ultimo != null ? ultimo.fechaProximaAccion() : null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String filtroConsolidadorSql(String tenant, UUID usuarioId, List<String> roles) {
        boolean esConsolidadorSolo = roles.contains("CONSOLIDACION_SEDE")
                && roles.stream().noneMatch(ROLES_SUPERIORES::contains);
        if (!esConsolidadorSolo) return "";

        UUID consolidadorMiembroId = buscarMiembroId(tenant, usuarioId);
        if (consolidadorMiembroId == null) return " AND 1=0"; // sin acceso
        return " AND consolidador_id = '" + consolidadorMiembroId + "'";
    }

    private void validarAsignacion(String tenant, UUID miembroId, UUID consolidadorMiembroId) {
        if (consolidadorMiembroId == null) {
            throw new AccessDeniedException(
                    "No tiene un perfil de miembro activo en esta sede");
        }
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros "
                + "WHERE id = ? AND consolidador_id = ? AND deleted_at IS NULL",
                Long.class, miembroId, consolidadorMiembroId);
        if (n == null || n == 0) {
            throw new AccessDeniedException(
                    "Solo puede registrar contactos para sus miembros asignados");
        }
    }

    private UUID buscarMiembroId(String tenant, UUID usuarioId) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM " + tenant + ".miembros "
                + "WHERE usuario_id = ? AND deleted_at IS NULL",
                UUID.class, usuarioId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private UUID buscarSedeId(String tenant) {
        return jdbc.queryForObject(
                "SELECT id FROM shared.sedes WHERE schema_name = ?",
                UUID.class, tenant);
    }

    private ContactoResponse mapContacto(ResultSet rs, int i) throws SQLException {
        Date fechaSql = rs.getDate("fecha");
        Date fechaProxSql = rs.getDate("fecha_proxima_accion");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new ContactoResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("miembro_id", UUID.class),
                rs.getObject("consolidador_id", UUID.class),
                fechaSql != null ? fechaSql.toLocalDate() : null,
                rs.getString("tipo"),
                rs.getString("resumen"),
                rs.getString("proxima_accion"),
                fechaProxSql != null ? fechaProxSql.toLocalDate() : null,
                rs.getBoolean("recordatorio_activo"),
                createdAt != null ? createdAt.toInstant() : null);
    }

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) throw new IllegalStateException("No hay tenant activo");
        return t;
    }
}
