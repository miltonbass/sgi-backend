// src/main/java/com/miltonbass/sgi_backend/miembros/service/MiembroService.java
package com.miltonbass.sgi_backend.miembros.service;

import com.miltonbass.sgi_backend.exception.AuthException;
import com.miltonbass.sgi_backend.miembros.dto.MiembroDtos.*;
import com.miltonbass.sgi_backend.miembros.entity.Miembro;
import com.miltonbass.sgi_backend.miembros.repository.MiembroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

        // Validación email único en el schema del tenant
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

        // Retornar el response directamente sin pasar por el repo (evita el bug)
        return new MiembroResponse(
                id, sedeId, creadoPorId,
                req.numeroMiembro(), req.cedula(),
                req.nombres(), req.apellidos(), req.fechaNacimiento(),
                req.genero(), req.estadoCivil(),
                req.telefono(), req.email(), req.direccion(), req.ciudad(),
                req.fotoUrl(), "VISITOR",
                req.fechaIngreso() != null ? req.fechaIngreso() : hoy,
                req.fechaBautismo(), req.grupoId(),
                req.metadata() != null ? req.metadata() : Map.of(),
                ahora, ahora);
    }

    // ── Actualizar ────────────────────────────────────────────────────
    @Transactional
    public MiembroResponse actualizar(UUID id, UpdateMiembroRequest req) {
        Miembro m = buscarPorId(id);

        if (req.email() != null && !req.email().equals(m.getEmail())) {
            if (miembroRepo.countByEmailExcluyendo(req.email(), id.toString()) > 0) {
                throw new IllegalArgumentException(
                        "Ya existe un miembro con el email: " + req.email());
            }
        }

        if (req.estado() != null && !req.estado().equals(m.getEstado())) {
            validarTransicion(m.getEstado(), req.estado(), id);
            m.setEstado(req.estado());
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

        m = miembroRepo.save(m);
        log.info("[Miembros] Actualizado: {}", id);
        return toResponse(m);
    }

    // ── Eliminar (soft delete) ────────────────────────────────────────
    @Transactional
    public void eliminar(UUID id) {
        Miembro m = buscarPorId(id);
        m.setDeletedAt(Instant.now());
        m.setEstado("INACTIVO");
        miembroRepo.save(m);
        log.info("[Miembros] Eliminado (soft): {}", id);
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
                m.getMetadata(), m.getCreadoEn(), m.getActualizadoEn());
    }
}