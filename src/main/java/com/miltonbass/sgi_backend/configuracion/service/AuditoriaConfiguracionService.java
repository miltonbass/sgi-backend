package com.miltonbass.sgi_backend.configuracion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditoriaConfiguracionService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaConfiguracionService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditoriaConfiguracionService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc   = jdbc;
        this.mapper = mapper;
    }

    // ── Registro (llamado desde el controller tras cada write exitoso) ─────────

    public void registrar(UUID sedeId, UUID usuarioId, String email,
                          String seccion, String accion, Map<String, Object> detalle) {
        try {
            String json = mapper.writeValueAsString(detalle);
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO shared.auditoria_configuracion
                            (sede_id, usuario_id, usuario_email, seccion, accion, detalle, realizado_en)
                        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), now())
                        """);
                ps.setObject(1, sedeId);
                ps.setObject(2, usuarioId);
                ps.setString(3, email);
                ps.setString(4, seccion);
                ps.setString(5, accion);
                ps.setObject(6, json, java.sql.Types.OTHER);
                return ps;
            });
        } catch (Exception e) {
            log.error("[AUDITORIA] Error registrando {}/{}: {}", seccion, accion, e.getMessage());
        }
    }

    // ── Consulta paginada ─────────────────────────────────────────────────────

    public AuditoriaPageResponse listar(UUID sedeId, String seccion,
                                        LocalDate fechaDesde, LocalDate fechaHasta,
                                        int pagina, int tamano) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE sede_id = ?");
        params.add(sedeId);

        if (seccion != null && !seccion.isBlank()) {
            where.append(" AND seccion = ?");
            params.add(seccion.trim());
        }
        if (fechaDesde != null) {
            where.append(" AND realizado_en >= ?");
            params.add(java.sql.Timestamp.from(fechaDesde.atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (fechaHasta != null) {
            where.append(" AND realizado_en < ?");
            params.add(java.sql.Timestamp.from(
                    fechaHasta.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        }

        String base = "FROM shared.auditoria_configuracion " + where;

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + base, Long.class, params.toArray());
        if (total == null) total = 0L;

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(tamano);
        pageParams.add((long) pagina * tamano);

        List<AuditoriaRegistroResponse> registros = jdbc.query(
                "SELECT id, usuario_email, seccion, accion, detalle::text, realizado_en "
                + base + " ORDER BY realizado_en DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> det = Map.of();
                    String raw = rs.getString("detalle");
                    if (raw != null) {
                        try { det = mapper.readValue(raw, new TypeReference<>() {}); }
                        catch (Exception ex) { det = Map.of(); }
                    }
                    return new AuditoriaRegistroResponse(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("usuario_email"),
                            rs.getString("seccion"),
                            rs.getString("accion"),
                            det,
                            rs.getTimestamp("realizado_en").toInstant());
                },
                pageParams.toArray());

        int totalPaginas = tamano > 0 ? (int) Math.ceil((double) total / tamano) : 0;
        return new AuditoriaPageResponse(registros, pagina, tamano, total, totalPaginas);
    }
}
