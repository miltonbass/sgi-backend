package com.miltonbass.sgi_backend.sedes.service;

import com.miltonbass.sgi_backend.auth.entity.Sede;
import com.miltonbass.sgi_backend.auth.repository.SedeRepository;
import com.miltonbass.sgi_backend.exception.AuthException;
import com.miltonbass.sgi_backend.sedes.dto.SedeDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SedeService {

    private static final Logger log = LoggerFactory.getLogger(SedeService.class);
    private static final String SEDE_PRINCIPAL = "PAI_BOG";

    private final SedeRepository sedeRepo;
    private final JdbcTemplate   jdbc;

    public SedeService(SedeRepository sedeRepo, JdbcTemplate jdbc) {
        this.sedeRepo = sedeRepo;
        this.jdbc     = jdbc;
    }

    // ── Listar (paginado) ────────────────────────────────────────────
    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public SedePageResponse listar(Pageable pageable) {
        Page<Sede> page = sedeRepo.findAll(pageable);
        return new SedePageResponse(
            page.getContent().stream().map(this::toResponse).toList(),
            page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages()
        );
    }

    // ── Obtener por ID ───────────────────────────────────────────────
    @Transactional(readOnly = true)
    public SedeResponse obtener(UUID id) {
        return toResponse(buscarPorId(id));
    }

    // ── Crear ────────────────────────────────────────────────────────
    // SIN @Transactional: fn_crear_schema_sede ejecuta DDL (CREATE SCHEMA)
    // que PostgreSQL no permite dentro de una transacción activa.
    public SedeResponse crear(CreateSedeRequest req) {
        if (sedeRepo.existsByCodigo(req.codigo())) {
            throw AuthException.codigoDuplicado(req.codigo());
        }

        String schemaName = "sede_" + req.codigo().toLowerCase();

        Sede sede = new Sede();
        sede.setCodigo(req.codigo());
        sede.setNombre(req.nombre());
        sede.setSchemaName(schemaName);
        sede.setDescripcion(req.descripcion());
        sede.setCiudad(req.ciudad());
        sede.setDepartamento(req.departamento());
        sede.setPais(req.pais() != null ? req.pais() : "Colombia");
        sede.setDireccion(req.direccion());
        sede.setTelefono(req.telefono());
        sede.setEmail(req.email());
        sede.setLogoUrl(req.logoUrl());
        sede.setZonaHoraria(req.zonaHoraria() != null
            ? req.zonaHoraria() : "America/Bogota");
        sede.setFechaFundacion(req.fechaFundacion());
        sede.setConfig(req.config() != null ? req.config() : Map.of());
        sede.setActiva(true);

        sedeRepo.save(sede);
        log.info("[Sedes] Sede {} guardada, id={}", req.codigo(), sede.getId());

        // Llamar a la función PG que crea el schema + todas sus tablas
        log.info("[Sedes] Provisionando schema {}...", schemaName);
        jdbc.query("SELECT shared.fn_crear_schema_sede(?, ?)",
        ps -> { ps.setString(1, schemaName); ps.setObject(2, sede.getId()); },
        rs -> null);
        log.info("[Sedes] Schema {} listo", schemaName);

        return toResponse(sede);
    }

    // ── Actualizar ───────────────────────────────────────────────────
    @Transactional
    public SedeResponse actualizar(UUID id, UpdateSedeRequest req) {
        Sede sede = buscarPorId(id);
        if (req.nombre()         != null) sede.setNombre(req.nombre());
        if (req.descripcion()    != null) sede.setDescripcion(req.descripcion());
        if (req.ciudad()         != null) sede.setCiudad(req.ciudad());
        if (req.departamento()   != null) sede.setDepartamento(req.departamento());
        if (req.pais()           != null) sede.setPais(req.pais());
        if (req.direccion()      != null) sede.setDireccion(req.direccion());
        if (req.telefono()       != null) sede.setTelefono(req.telefono());
        if (req.email()          != null) sede.setEmail(req.email());
        if (req.logoUrl()        != null) sede.setLogoUrl(req.logoUrl());
        if (req.zonaHoraria()    != null) sede.setZonaHoraria(req.zonaHoraria());
        if (req.fechaFundacion() != null) sede.setFechaFundacion(req.fechaFundacion());
        if (req.config()         != null) sede.setConfig(req.config());
        sede.setActualizadoEn(Instant.now());
        return toResponse(sedeRepo.save(sede));
    }

    // ── Desactivar (delete lógico) ───────────────────────────────────
    @Transactional
    public void desactivar(UUID id) {
        Sede sede = buscarPorId(id);
        if (SEDE_PRINCIPAL.equals(sede.getCodigo())) {
            throw AuthException.sedePrincipalProtegida();
        }
        if (!sede.isActiva()) {
            throw AuthException.sedeYaInactiva();
        }
        sede.setActiva(false);
        sede.setDeletedAt(Instant.now());
        sede.setActualizadoEn(Instant.now());
        sedeRepo.save(sede);
        log.info("[Sedes] Sede {} desactivada", sede.getCodigo());
    }

    // ── Helpers ──────────────────────────────────────────────────────
    @SuppressWarnings("null")
    private Sede buscarPorId(UUID id) {
        return sedeRepo.findById(id)
            .orElseThrow(AuthException::sedeNoEncontrada);
    }

    private SedeResponse toResponse(Sede s) {
        return new SedeResponse(
            s.getId(), s.getCodigo(), s.getNombre(), s.getSchemaName(),
            s.getDescripcion(), s.getCiudad(), s.getDepartamento(), s.getPais(),
            s.getDireccion(), s.getTelefono(), s.getEmail(), s.getLogoUrl(),
            s.getZonaHoraria(), s.isActiva(), s.getFechaFundacion(),
            s.getConfig(), s.getCreadoEn(), s.getActualizadoEn()
        );
    }
}
