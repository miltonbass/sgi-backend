package com.miltonbass.sgi_backend.eventos.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.eventos.dto.EventoDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class EventoService {

    private static final Logger log = LoggerFactory.getLogger(EventoService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper  mapper;

    public EventoService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc   = jdbc;
        this.mapper = mapper;
    }

    // ── Listar con filtros ────────────────────────────────────────────────────
    public EventoPageResponse listar(String tipo, String estado,
                                     Instant desde, Instant hasta,
                                     int page, int size) {
        String tenant = tenant();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE deleted_at IS NULL ");

        if (tipo != null && !tipo.isBlank()) {
            where.append("AND tipo = ? ");
            params.add(tipo.toUpperCase());
        }
        if (estado != null && !estado.isBlank()) {
            where.append("AND estado = ? ");
            params.add(estado.toUpperCase());
        }
        if (desde != null) {
            where.append("AND fecha_inicio >= ? ");
            params.add(Timestamp.from(desde));
        }
        if (hasta != null) {
            where.append("AND fecha_inicio <= ? ");
            params.add(Timestamp.from(hasta));
        }

        String countSql = "SELECT COUNT(*) FROM " + tenant + ".eventos " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        params.add(size);
        params.add((long) page * size);
        String dataSql = "SELECT * FROM " + tenant + ".eventos " + where
                + "ORDER BY fecha_inicio ASC LIMIT ? OFFSET ?";

        List<EventoResponse> content = jdbc.query(dataSql, this::mapRow, params.toArray());

        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new EventoPageResponse(content, page, size, total, totalPages);
    }

    // ── Obtener por ID ────────────────────────────────────────────────────────
    public EventoResponse obtener(UUID id) {
        String tenant = tenant();
        return jdbc.query(
                "SELECT * FROM " + tenant + ".eventos WHERE id = ? AND deleted_at IS NULL",
                this::mapRow, id)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado: " + id));
    }

    // ── Crear ─────────────────────────────────────────────────────────────────
    public EventoResponse crear(CreateEventoRequest req, UUID sedeId, UUID creadoPor) {
        String tenant = tenant();

        if (req.recurrente() && req.patronRecurrencia() == null) {
            throw new IllegalArgumentException(
                    "patronRecurrencia es requerido cuando recurrente=true");
        }
        if (req.fechaFin() != null && req.fechaFin().isBefore(req.fechaInicio())) {
            throw new IllegalArgumentException("fechaFin no puede ser anterior a fechaInicio");
        }

        UUID id    = UUID.randomUUID();
        Instant now = Instant.now();
        String patronJson = toJson(req.patronRecurrencia());

        jdbc.update(
                "INSERT INTO " + tenant + ".eventos "
                + "(id, sede_id, titulo, tipo, estado, descripcion, fecha_inicio, fecha_fin, "
                + " lugar, capacidad, recurrente, patron_recurrencia, creado_por, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                id, sedeId, req.titulo(), req.tipo(), "PROGRAMADO",
                req.descripcion(),
                Timestamp.from(req.fechaInicio()),
                req.fechaFin() != null ? Timestamp.from(req.fechaFin()) : null,
                req.lugar(), req.capacidad(), req.recurrente(),
                patronJson, creadoPor,
                Timestamp.from(now), Timestamp.from(now));

        log.info("[Eventos] Creado: '{}' tipo={} | sede={}", req.titulo(), req.tipo(), sedeId);
        return obtener(id);
    }

    // ── Actualizar ────────────────────────────────────────────────────────────
    public EventoResponse actualizar(UUID id, UpdateEventoRequest req) {
        String tenant = tenant();
        EventoResponse actual = obtener(id);

        if ("CERRADO".equals(actual.estado()) || "CANCELADO".equals(actual.estado())) {
            throw new IllegalArgumentException(
                    "No se puede modificar un evento en estado " + actual.estado());
        }
        if (req.fechaFin() != null && req.fechaInicio() != null
                && req.fechaFin().isBefore(req.fechaInicio())) {
            throw new IllegalArgumentException("fechaFin no puede ser anterior a fechaInicio");
        }

        String titulo      = req.titulo()      != null ? req.titulo()      : actual.titulo();
        String tipo        = req.tipo()        != null ? req.tipo()        : actual.tipo();
        String descripcion = req.descripcion() != null ? req.descripcion() : actual.descripcion();
        Instant fechaInicio = req.fechaInicio() != null ? req.fechaInicio() : actual.fechaInicio();
        Instant fechaFin    = req.fechaFin()    != null ? req.fechaFin()    : actual.fechaFin();
        String lugar       = req.lugar()       != null ? req.lugar()       : actual.lugar();
        Integer capacidad  = req.capacidad()   != null ? req.capacidad()   : actual.capacidad();
        boolean recurrente = req.recurrente()  != null ? req.recurrente()  : actual.recurrente();
        String patronJson  = req.patronRecurrencia() != null
                ? toJson(req.patronRecurrencia()) : toJson(actual.patronRecurrencia());

        jdbc.update(
                "UPDATE " + tenant + ".eventos SET "
                + "titulo=?, tipo=?, descripcion=?, fecha_inicio=?, fecha_fin=?, "
                + "lugar=?, capacidad=?, recurrente=?, patron_recurrencia=?::jsonb "
                + "WHERE id = ?",
                titulo, tipo, descripcion,
                Timestamp.from(fechaInicio),
                fechaFin != null ? Timestamp.from(fechaFin) : null,
                lugar, capacidad, recurrente, patronJson, id);

        log.info("[Eventos] Actualizado: {}", id);
        return obtener(id);
    }

    // ── Cambiar estado ────────────────────────────────────────────────────────
    public EventoResponse cambiarEstado(UUID id, CambiarEstadoEventoRequest req) {
        String tenant = tenant();
        EventoResponse actual = obtener(id);

        validarTransicionEstado(actual.estado(), req.estado(), id);

        jdbc.update("UPDATE " + tenant + ".eventos SET estado = ? WHERE id = ?",
                req.estado(), id);

        log.info("[Eventos] Estado: {} → {} | id={}", actual.estado(), req.estado(), id);
        return obtener(id);
    }

    // ── Cancelar (soft delete) ────────────────────────────────────────────────
    public void cancelar(UUID id) {
        String tenant = tenant();
        EventoResponse actual = obtener(id);

        if ("CERRADO".equals(actual.estado())) {
            throw new IllegalArgumentException("No se puede cancelar un evento ya cerrado");
        }

        jdbc.update(
                "UPDATE " + tenant + ".eventos SET estado='CANCELADO', deleted_at=NOW() WHERE id=?",
                id);
        log.info("[Eventos] Cancelado: {}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validarTransicionEstado(String actual, String nuevo, UUID id) {
        Map<String, Set<String>> transiciones = Map.of(
                "PROGRAMADO", Set.of("ABIERTO", "CANCELADO"),
                "ABIERTO",    Set.of("CERRADO", "CANCELADO"),
                "CERRADO",    Set.of(),
                "CANCELADO",  Set.of());

        if (!transiciones.getOrDefault(actual, Set.of()).contains(nuevo)) {
            throw new IllegalArgumentException(
                    "Transición no permitida: " + actual + " → " + nuevo + " (evento " + id + ")");
        }
    }

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }
        return t;
    }

    private EventoResponse mapRow(ResultSet rs, int i) throws SQLException {
        Timestamp fi  = rs.getTimestamp("fecha_inicio");
        Timestamp ff  = rs.getTimestamp("fecha_fin");
        Timestamp cat = rs.getTimestamp("created_at");
        Timestamp uat = rs.getTimestamp("updated_at");

        return new EventoResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("sede_id", UUID.class),
                rs.getString("titulo"),
                rs.getString("tipo"),
                rs.getString("estado"),
                rs.getString("descripcion"),
                fi  != null ? fi.toInstant()  : null,
                ff  != null ? ff.toInstant()  : null,
                rs.getString("lugar"),
                (Integer) rs.getObject("capacidad"),
                rs.getBoolean("recurrente"),
                fromJson(rs.getString("patron_recurrencia")),
                rs.getObject("creado_por", UUID.class),
                cat != null ? cat.toInstant() : null,
                uat != null ? uat.toInstant() : null);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return mapper.writeValueAsString(obj); }
        catch (Exception e) { return null; }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return null; }
    }
}
