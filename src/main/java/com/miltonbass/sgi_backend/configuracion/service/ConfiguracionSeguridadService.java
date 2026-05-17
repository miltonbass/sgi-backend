package com.miltonbass.sgi_backend.configuracion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfiguracionSeguridadService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionSeguridadService.class);
    private static final String DOMINIO = "seguridad";

    private static final int DEFAULT_LONGITUD_MINIMA       = 8;
    private static final int DEFAULT_EXPIRACION_DIAS       = 0;
    private static final int DEFAULT_MAX_INTENTOS          = 5;
    private static final int DEFAULT_DURACION_SESION_HORAS = 1;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ConfiguracionSeguridadService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc   = jdbc;
        this.mapper = mapper;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    public PoliticasSeguridadResponse obtener() {
        Map<String, Object> cfg = leerConfig();
        return toResponse(cfg != null ? cfg : Map.of());
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    public PoliticasSeguridadResponse actualizar(ActualizarSeguridadRequest req, UUID usuarioId) {
        Map<String, Object> cfg = leerConfig();
        if (cfg == null) cfg = new HashMap<>();

        cfg.put("longitudMinimaPassword", req.longitudMinimaPassword());
        cfg.put("expiracionPasswordDias", req.expiracionPasswordDias());
        cfg.put("maxIntentosFallidos",    req.maxIntentosFallidos());
        cfg.put("duracionSesionHoras",    req.duracionSesionHoras());

        guardar(cfg, usuarioId);
        log.info("[SEGURIDAD] Politicas actualizadas por {}", usuarioId);
        return toResponse(cfg);
    }

    // ── Consumido por JwtService ──────────────────────────────────────────────

    public int getDuracionSesionMinutos() {
        try {
            Map<String, Object> cfg = leerConfig();
            if (cfg == null) return DEFAULT_DURACION_SESION_HORAS * 60;
            return getInt(cfg, "duracionSesionHoras", DEFAULT_DURACION_SESION_HORAS) * 60;
        } catch (Exception e) {
            log.warn("[SEGURIDAD] Error leyendo duracion sesion, usando default: {}", e.getMessage());
            return DEFAULT_DURACION_SESION_HORAS * 60;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PoliticasSeguridadResponse toResponse(Map<String, Object> cfg) {
        return new PoliticasSeguridadResponse(
                getInt(cfg, "longitudMinimaPassword", DEFAULT_LONGITUD_MINIMA),
                getInt(cfg, "expiracionPasswordDias", DEFAULT_EXPIRACION_DIAS),
                getInt(cfg, "maxIntentosFallidos",    DEFAULT_MAX_INTENTOS),
                getInt(cfg, "duracionSesionHoras",    DEFAULT_DURACION_SESION_HORAS));
    }

    private Map<String, Object> leerConfig() {
        List<String> rows = jdbc.queryForList(
                "SELECT config::text FROM shared.configuracion_global WHERE dominio = ?",
                String.class, DOMINIO);
        if (rows.isEmpty() || rows.get(0) == null) return null;
        try {
            return mapper.readValue(rows.get(0), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[SEGURIDAD] Error parseando config JSONB: {}", e.getMessage());
            return null;
        }
    }

    private void guardar(Map<String, Object> cfg, UUID usuarioId) {
        try {
            String json = mapper.writeValueAsString(cfg);
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO shared.configuracion_global (dominio, config, updated_at, updated_by)
                        VALUES (?, CAST(? AS jsonb), now(), ?)
                        ON CONFLICT (dominio) DO UPDATE
                        SET config     = EXCLUDED.config,
                            updated_at = now(),
                            updated_by = EXCLUDED.updated_by
                        """);
                ps.setString(1, DOMINIO);
                ps.setObject(2, json, java.sql.Types.OTHER);
                ps.setObject(3, usuarioId);
                return ps;
            });
        } catch (Exception e) {
            throw new RuntimeException("Error guardando config de seguridad: " + e.getMessage(), e);
        }
    }

    private int getInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }
}
