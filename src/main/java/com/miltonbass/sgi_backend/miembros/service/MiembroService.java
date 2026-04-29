// src/main/java/com/miltonbass/sgi_backend/miembros/service/MiembroService.java
package com.miltonbass.sgi_backend.miembros.service;

import com.miltonbass.sgi_backend.exception.AuthException;
import com.miltonbass.sgi_backend.miembros.dto.MiembroDtos.*;
import org.springframework.security.access.AccessDeniedException;
import com.miltonbass.sgi_backend.miembros.entity.Miembro;
import com.miltonbass.sgi_backend.miembros.repository.MiembroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MiembroService {

    private static final Logger log = LoggerFactory.getLogger(MiembroService.class);

    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "VISITOR", Set.of("MIEMBRO", "INACTIVO"),
            "MIEMBRO", Set.of("INACTIVO", "RESTAURADO"),
            "INACTIVO", Set.of("RESTAURADO"),
            "RESTAURADO", Set.of("MIEMBRO", "INACTIVO"));

    private final MiembroRepository miembroRepo;
    private final JdbcTemplate jdbc;

    public MiembroService(MiembroRepository miembroRepo, JdbcTemplate jdbc) {
        this.miembroRepo = miembroRepo;
        this.jdbc = jdbc;
    }

    // ── Listar ────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public MiembroPageResponse listar(String estado, Pageable pageable) {
        Page<Miembro> page = (estado != null && !estado.isBlank())
                ? miembroRepo.findAllByEstadoAndDeletedAtIsNull(estado, pageable)
                : miembroRepo.findAllByDeletedAtIsNull(pageable);
        return toPageResponse(page);
    }

    // ── Buscar ────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public MiembroPageResponse buscar(String q, String estado, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return listar(estado, pageable);
        }
        return toPageResponse(miembroRepo.buscar(q.trim(), estado, pageable));
    }

    // ── Obtener ───────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public MiembroResponse obtener(UUID id) {
        return toResponse(buscarPorId(id));
    }

    // ── Crear — sin @Transactional, usa JdbcTemplate para evitar
    // el problema de conexión cacheada con search_path=shared ────────
    public MiembroResponse crear(CreateMiembroRequest req,
            UUID sedeId,
            UUID creadoPorId) {
        String tenant = com.miltonbass.sgi_backend.config.TenantContext.getCurrentTenant();
        if (tenant == null || tenant.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tenant + ".miembros WHERE email = ? AND deleted_at IS NULL",
                Long.class, req.email());
        if (req.email() != null && count != null && count > 0) {
            throw new IllegalArgumentException(
                    "Ya existe un miembro con el email: " + req.email());
        }

        UUID id = UUID.randomUUID();
        Instant ahora = Instant.now();
        LocalDate hoy = LocalDate.now();

        String sql = "INSERT INTO " + tenant + ".miembros ("
                + "id, sede_id, creado_por, "
                + "nombres, apellidos, email, telefono, "
                + "fecha_nacimiento, direccion, estado_civil, "
                + "cedula, numero_miembro, genero, ciudad, foto_url, "
                + "fecha_ingreso, fecha_bautismo, grupo_id, "
                + "estado, created_at, updated_at"
                + ") VALUES ("
                + "?, ?, ?, "
                + "?, ?, ?, ?, "
                + "?, ?, ?, "
                + "?, ?, ?, ?, ?, "
                + "?, ?, ?, "
                + "?, ?, ?)";

        jdbc.update(sql,
                id, sedeId, creadoPorId,
                req.nombres(), req.apellidos(), req.email(), req.telefono(),
                req.fechaNacimiento(), req.direccion(), req.estadoCivil(),
                req.cedula(), req.numeroMiembro(), req.genero(),
                req.ciudad(), req.fotoUrl(),
                req.fechaIngreso() != null ? req.fechaIngreso() : hoy,
                req.fechaBautismo(), req.grupoId(),
                "VISITOR",
                Timestamp.from(ahora), Timestamp.from(ahora));

        log.info("[Miembros] Creado: {} {} | sede={} | creadoPor={}",
                req.nombres(), req.apellidos(), sedeId, creadoPorId);

        return new MiembroResponse(
                id, sedeId, creadoPorId,
                req.numeroMiembro(), req.cedula(),
                req.nombres(), req.apellidos(), req.fechaNacimiento(),
                req.genero(), req.estadoCivil(),
                req.telefono(), req.email(), req.direccion(), req.ciudad(),
                req.fotoUrl(), "VISITOR",
                req.fechaIngreso() != null ? req.fechaIngreso() : hoy,
                req.fechaBautismo(), req.grupoId(),
                null, // consolidadorId — nuevo miembro no tiene consolidador
                req.metadata() != null ? req.metadata() : Map.of(),
                ahora, ahora);
    }

    // ── Actualizar (datos del perfil) ────────────────────────────────
    @SuppressWarnings("null") // falso positivo JDT con generics de Spring Data save()
    @Transactional
    public MiembroResponse actualizar(UUID id, UpdateMiembroRequest req) {
        Miembro m = buscarPorId(id);

        if (req.email() != null && !req.email().equals(m.getEmail())) {
            if (miembroRepo.countByEmailExcluyendo(req.email(), id.toString()) > 0) {
                throw new IllegalArgumentException(
                        "Ya existe un miembro con el email: " + req.email());
            }
        }

        if (req.nombres() != null)
            m.setNombres(req.nombres());
        if (req.apellidos() != null)
            m.setApellidos(req.apellidos());
        if (req.email() != null)
            m.setEmail(req.email());
        if (req.telefono() != null)
            m.setTelefono(req.telefono());
        if (req.fechaNacimiento() != null)
            m.setFechaNacimiento(req.fechaNacimiento());
        if (req.direccion() != null)
            m.setDireccion(req.direccion());
        if (req.estadoCivil() != null)
            m.setEstadoCivil(req.estadoCivil());
        if (req.cedula() != null)
            m.setCedula(req.cedula());
        if (req.genero() != null)
            m.setGenero(req.genero());
        if (req.ciudad() != null)
            m.setCiudad(req.ciudad());
        if (req.fotoUrl() != null)
            m.setFotoUrl(req.fotoUrl());
        if (req.fechaIngreso() != null)
            m.setFechaIngreso(req.fechaIngreso());
        if (req.fechaBautismo() != null)
            m.setFechaBautismo(req.fechaBautismo());
        if (req.grupoId() != null)
            m.setGrupoId(req.grupoId());
        if (req.metadata() != null)
            m.setMetadata(req.metadata());

        Miembro saved = miembroRepo.save(m); // save() nunca retorna null — warning JDT es falso positivo
        log.info("[Miembros] Actualizado: {}", id);
        return toResponse(saved);
    }

    // ── Cambiar estado — JdbcTemplate + INSERT en historial ──────────
    public MiembroResponse cambiarEstado(UUID id, CambiarEstadoRequest req, UUID cambiadoPorId) {
        String tenant = com.miltonbass.sgi_backend.config.TenantContext.getCurrentTenant();
        if (tenant == null || tenant.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }

        Miembro m = buscarPorId(id);
        validarTransicion(m.getEstado(), req.estado(), id);

        String estadoAnterior = m.getEstado();

        jdbc.update(
                "UPDATE " + tenant + ".miembros SET estado = ? WHERE id = ?",
                req.estado(), id);

        jdbc.update(
                "INSERT INTO " + tenant + ".miembro_estado_historial "
                + "(id, miembro_id, estado_anterior, estado_nuevo, motivo, cambiado_por) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), id, estadoAnterior, req.estado(),
                req.motivo(), cambiadoPorId);

        log.info("[Miembros] Estado cambiado: {} → {} | miembro={}", estadoAnterior, req.estado(), id);

        m.setEstado(req.estado());
        return toResponse(m);
    }

    // ── Obtener historial de estado ───────────────────────────────────
    public EstadoHistorialResponse obtenerHistorialEstado(UUID id) {
        String tenant = com.miltonbass.sgi_backend.config.TenantContext.getCurrentTenant();
        if (tenant == null || tenant.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }

        buscarPorId(id); // valida que el miembro existe

        String sql = "SELECT id, estado_anterior, estado_nuevo, motivo, cambiado_por, cambiado_en "
                + "FROM " + tenant + ".miembro_estado_historial "
                + "WHERE miembro_id = ? ORDER BY cambiado_en DESC";

        List<EstadoHistorialItem> items = jdbc.query(sql, (rs, rowNum) -> new EstadoHistorialItem(
                rs.getObject("id", UUID.class),
                rs.getString("estado_anterior"),
                rs.getString("estado_nuevo"),
                rs.getString("motivo"),
                rs.getObject("cambiado_por", UUID.class),
                rs.getTimestamp("cambiado_en").toInstant()), id);

        return new EstadoHistorialResponse(items);
    }

    // ── Asignar / quitar consolidador ─────────────────────────────────
    public MiembroResponse asignarConsolidador(UUID id, AsignarConsolidadorRequest req) {
        String tenant = com.miltonbass.sgi_backend.config.TenantContext.getCurrentTenant();
        if (tenant == null || tenant.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }

        Miembro m = buscarPorId(id);

        if (!Set.of("MIEMBRO", "RESTAURADO").contains(m.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se puede asignar consolidador a miembros en estado MIEMBRO o RESTAURADO. "
                    + "Estado actual: " + m.getEstado());
        }

        if (req.consolidadorId() != null) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + tenant + ".miembros WHERE id = ? AND deleted_at IS NULL",
                    Long.class, req.consolidadorId());
            if (count == null || count == 0) {
                throw new IllegalArgumentException("El consolidador especificado no existe en esta sede");
            }
        }

        jdbc.update(
                "UPDATE " + tenant + ".miembros SET consolidador_id = ? WHERE id = ?",
                req.consolidadorId(), id);

        log.info("[Miembros] Consolidador {}: miembro={} consolidador={}",
                req.consolidadorId() == null ? "quitado" : "asignado", id, req.consolidadorId());

        m.setConsolidadorId(req.consolidadorId());
        return toResponse(m);
    }

    // ── Perfil detallado (H2.5) ───────────────────────────────────────
    private static final Set<String> ROLES_ADMIN     = Set.of("ADMIN_GLOBAL", "ADMIN_SEDE");
    private static final Set<String> ROLES_COMPLETO  = Set.of("ADMIN_GLOBAL", "ADMIN_SEDE",
            "PASTOR_SEDE", "PASTOR_PRINCIPAL", "SECRETARIA", "CONSOLIDACION_SEDE");

    public PerfilMiembroResponse obtenerPerfil(UUID miembroId, UUID usuarioId, List<String> roles) {
        String tenant = com.miltonbass.sgi_backend.config.TenantContext.getCurrentTenant();
        if (tenant == null || tenant.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }

        Miembro m = buscarPorId(miembroId);

        boolean esAdmin         = roles.stream().anyMatch(ROLES_ADMIN::contains);
        boolean esPastorSede    = roles.contains("PASTOR_SEDE");
        boolean esConsolidador  = roles.contains("CONSOLIDACION_SEDE") && !esAdmin && !esPastorSede;
        boolean esPastorPpal    = roles.contains("PASTOR_PRINCIPAL")   && !esAdmin && !esPastorSede;
        boolean esRegistroSolo  = roles.contains("REGISTRO_SEDE")
                && roles.stream().noneMatch(ROLES_COMPLETO::contains);

        // CONSOLIDACION_SEDE: solo puede ver los miembros que consolida
        if (esConsolidador) {
            verificarAccesoConsolidador(tenant, miembroId, usuarioId);
        }

        // Datos personales (teléfono y dirección ocultos para PASTOR_PRINCIPAL)
        MiembroResponse datos = buildDatosPersonales(m, esPastorPpal);

        // REGISTRO_SEDE: solo datos básicos, sin sección pastoral
        if (esRegistroSolo) {
            return new PerfilMiembroResponse(datos, List.of(), List.of(), List.of(), "BASICO");
        }

        List<EstadoHistorialItem> historial  = fetchHistorialPerfil(tenant, miembroId);
        List<GrupoMembresiaItem>  grupos     = fetchGruposPerfil(tenant, miembroId);
        List<AsistenciaItem>      asistencia = fetchAsistenciaReciente(tenant, miembroId);

        return new PerfilMiembroResponse(datos, historial, grupos, asistencia, "COMPLETO");
    }

    private void verificarAccesoConsolidador(String tenant, UUID miembroId, UUID usuarioId) {
        // Buscar el miembro cuyo usuario_id sea el del solicitante
        List<UUID> misIds = jdbc.queryForList(
                "SELECT id FROM " + tenant + ".miembros WHERE usuario_id = ? AND deleted_at IS NULL",
                UUID.class, usuarioId);

        if (misIds.isEmpty()) {
            throw new AccessDeniedException(
                    "No tiene un perfil de miembro activo en esta sede");
        }

        boolean tieneAcceso = misIds.stream().anyMatch(miId -> {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + tenant + ".miembros WHERE id = ? AND consolidador_id = ? AND deleted_at IS NULL",
                    Long.class, miembroId, miId);
            return n != null && n > 0;
        });

        if (!tieneAcceso) {
            throw new AccessDeniedException(
                    "CONSOLIDACION_SEDE solo puede ver el perfil de sus miembros asignados");
        }
    }

    private MiembroResponse buildDatosPersonales(Miembro m, boolean enmascararSensibles) {
        return new MiembroResponse(
                m.getId(), m.getSedeId(), m.getCreadoPor(),
                m.getNumeroMiembro(), m.getCedula(),
                m.getNombres(), m.getApellidos(), m.getFechaNacimiento(),
                m.getGenero(), m.getEstadoCivil(),
                enmascararSensibles ? null : m.getTelefono(),
                m.getEmail(),
                enmascararSensibles ? null : m.getDireccion(),
                m.getCiudad(), m.getFotoUrl(), m.getEstado(),
                m.getFechaIngreso(), m.getFechaBautismo(), m.getGrupoId(),
                m.getConsolidadorId(), m.getMetadata(),
                m.getCreadoEn(), m.getActualizadoEn());
    }

    private List<EstadoHistorialItem> fetchHistorialPerfil(String tenant, UUID miembroId) {
        return jdbc.query(
                "SELECT id, estado_anterior, estado_nuevo, motivo, cambiado_por, cambiado_en "
                + "FROM " + tenant + ".miembro_estado_historial "
                + "WHERE miembro_id = ? ORDER BY cambiado_en DESC",
                (rs, i) -> new EstadoHistorialItem(
                        rs.getObject("id", UUID.class),
                        rs.getString("estado_anterior"),
                        rs.getString("estado_nuevo"),
                        rs.getString("motivo"),
                        rs.getObject("cambiado_por", UUID.class),
                        rs.getTimestamp("cambiado_en").toInstant()),
                miembroId);
    }

    private List<GrupoMembresiaItem> fetchGruposPerfil(String tenant, UUID miembroId) {
        return jdbc.query(
                "SELECT mg.grupo_id, g.nombre, g.tipo, mg.rol, mg.fecha_ingreso "
                + "FROM " + tenant + ".miembro_grupos mg "
                + "JOIN " + tenant + ".grupos g ON g.id = mg.grupo_id "
                + "WHERE mg.miembro_id = ? ORDER BY mg.fecha_ingreso DESC",
                (rs, i) -> {
                    Date fi = rs.getDate("fecha_ingreso");
                    return new GrupoMembresiaItem(
                            rs.getObject("grupo_id", UUID.class),
                            rs.getString("nombre"),
                            rs.getString("tipo"),
                            rs.getString("rol"),
                            fi != null ? fi.toLocalDate() : null);
                },
                miembroId);
    }

    private List<AsistenciaItem> fetchAsistenciaReciente(String tenant, UUID miembroId) {
        return jdbc.query(
                "SELECT evento_id, presente, observacion, created_at "
                + "FROM " + tenant + ".asistencias "
                + "WHERE miembro_id = ? ORDER BY created_at DESC LIMIT 10",
                (rs, i) -> new AsistenciaItem(
                        rs.getObject("evento_id", UUID.class),
                        rs.getBoolean("presente"),
                        rs.getString("observacion"),
                        rs.getTimestamp("created_at").toInstant()),
                miembroId);
    }

    // ── Eliminar — alias de transición a INACTIVO con motivo ─────────
    public void eliminar(UUID id, String motivo, UUID cambiadoPorId) {
        cambiarEstado(id, new CambiarEstadoRequest("INACTIVO", motivo), cambiadoPorId);
        log.info("[Miembros] Inactivado via DELETE: {}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void validarTransicion(String estadoActual, String estadoNuevo, UUID id) {
        Set<String> permitidos = TRANSICIONES.getOrDefault(estadoActual, Set.of());
        if (!permitidos.contains(estadoNuevo)) {
            throw new IllegalArgumentException(
                    "Transición de estado no permitida: "
                            + estadoActual + " → " + estadoNuevo
                            + " para miembro " + id);
        }
    }

    private Miembro buscarPorId(UUID id) {
        return miembroRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(AuthException::usuarioNoEncontrado);
    }

    private MiembroPageResponse toPageResponse(Page<Miembro> page) {
        return new MiembroPageResponse(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private MiembroResponse toResponse(Miembro m) {
        return new MiembroResponse(
                m.getId(), m.getSedeId(), m.getCreadoPor(),
                m.getNumeroMiembro(), m.getCedula(),
                m.getNombres(), m.getApellidos(), m.getFechaNacimiento(),
                m.getGenero(), m.getEstadoCivil(),
                m.getTelefono(), m.getEmail(), m.getDireccion(), m.getCiudad(),
                m.getFotoUrl(), m.getEstado(),
                m.getFechaIngreso(), m.getFechaBautismo(), m.getGrupoId(),
                m.getConsolidadorId(),
                m.getMetadata(), m.getCreadoEn(), m.getActualizadoEn());
    }
}
