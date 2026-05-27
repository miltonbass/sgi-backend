package com.miltonbass.sgi_backend.auditoria.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.auditoria.dto.AuditoriaDtos.*;
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
public class AuditoriaGeneralService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaGeneralService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditoriaGeneralService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc   = jdbc;
        this.mapper = mapper;
    }

    // ── Registro (llamado desde AuditoriaAspecto) ─────────────────────────────

    public void registrar(UUID usuarioId, String email, UUID sedeId,
                          String modulo, String accion, String entidadId,
                          String endpoint, String metodoHttp,
                          Map<String, Object> detalle, String ip,
                          String resultado, String errorMsg) {
        try {
            String json = mapper.writeValueAsString(detalle.isEmpty() ? null : detalle);
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO shared.auditoria_general
                            (usuario_id, usuario_email, sede_id, modulo, accion, entidad_id,
                             endpoint, metodo_http, detalle, ip, resultado, error_mensaje, realizado_en)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, now())
                        """);
                ps.setObject(1, usuarioId);
                ps.setString(2, email);
                ps.setObject(3, sedeId);
                ps.setString(4, modulo);
                ps.setString(5, accion);
                ps.setString(6, entidadId);
                ps.setString(7, endpoint);
                ps.setString(8, metodoHttp);
                ps.setObject(9, "null".equals(json) ? null : json, java.sql.Types.OTHER);
                ps.setString(10, ip);
                ps.setString(11, resultado);
                ps.setString(12, errorMsg);
                return ps;
            });
        } catch (Exception e) {
            log.error("[AUDITORIA] Error registrando {}/{}: {}", modulo, accion, e.getMessage());
        }
    }

    // ── Consulta paginada ─────────────────────────────────────────────────────

    public AuditoriaPageResponse listar(UUID sedeId, boolean esGlobal,
                                        String modulo, String accion,
                                        String usuarioEmail, String resultado,
                                        LocalDate fechaDesde, LocalDate fechaHasta,
                                        int pagina, int tamano) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");

        if (!esGlobal && sedeId != null) {
            where.append(" AND sede_id = ?");
            params.add(sedeId);
        }
        if (modulo != null && !modulo.isBlank()) {
            where.append(" AND modulo = ?");
            params.add(modulo.trim().toUpperCase());
        }
        if (accion != null && !accion.isBlank()) {
            where.append(" AND accion = ?");
            params.add(accion.trim().toUpperCase());
        }
        if (usuarioEmail != null && !usuarioEmail.isBlank()) {
            where.append(" AND usuario_email ILIKE ?");
            params.add("%" + usuarioEmail.trim() + "%");
        }
        if (resultado != null && !resultado.isBlank()) {
            where.append(" AND resultado = ?");
            params.add(resultado.trim().toUpperCase());
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

        String base = "FROM shared.auditoria_general " + where;

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + base, Long.class, params.toArray());
        if (total == null) total = 0L;

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(tamano);
        pageParams.add((long) pagina * tamano);

        List<AuditoriaGeneralResponse> registros = jdbc.query(
                "SELECT id, usuario_email, modulo, accion, entidad_id, endpoint, "
                + "metodo_http, detalle::text, ip, resultado, error_mensaje, realizado_en "
                + base + " ORDER BY realizado_en DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Map<String, Object> det = Map.of();
                    String raw = rs.getString("detalle");
                    if (raw != null) {
                        try { det = mapper.readValue(raw, new TypeReference<>() {}); }
                        catch (Exception ex) { det = Map.of(); }
                    }
                    return new AuditoriaGeneralResponse(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("usuario_email"),
                            rs.getString("modulo"),
                            rs.getString("accion"),
                            rs.getString("entidad_id"),
                            rs.getString("endpoint"),
                            rs.getString("metodo_http"),
                            det,
                            rs.getString("ip"),
                            rs.getString("resultado"),
                            rs.getString("error_mensaje"),
                            rs.getTimestamp("realizado_en").toInstant());
                },
                pageParams.toArray());

        int totalPaginas = tamano > 0 ? (int) Math.ceil((double) total / tamano) : 0;
        return new AuditoriaPageResponse(registros, pagina, tamano, total, totalPaginas);
    }
}
