package com.miltonbass.sgi_backend.configuracion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfiguracionDominioService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionDominioService.class);
    private static final String DOMINIO = "dominio";

    private final JdbcTemplate jdbc;
    private final ObjectMapper  mapper;
    private final String        defaultDominioApp;

    public ConfiguracionDominioService(JdbcTemplate jdbc,
                                       ObjectMapper mapper,
                                       @Value("${sgi.app.url:http://localhost:4200}") String defaultDominioApp) {
        this.jdbc              = jdbc;
        this.mapper            = mapper;
        this.defaultDominioApp = defaultDominioApp;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    public ConfiguracionDominioResponse obtener() {
        Map<String, Object> cfg = leerConfig();
        if (cfg == null) {
            return new ConfiguracionDominioResponse(defaultDominioApp, null, defaultDominioApp, false);
        }
        return toResponse(cfg);
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    public ConfiguracionDominioResponse actualizar(ActualizarDominioRequest req, UUID usuarioId) {
        Map<String, Object> cfg = leerConfig();
        if (cfg == null) cfg = new HashMap<>();

        cfg.put("dominioApp",   req.dominioApp());
        cfg.put("dominioApi",   req.dominioApi() != null ? req.dominioApi() : req.dominioApp());
        cfg.put("corsOrigenes", normalizarCors(req.corsOrigenes(), req.dominioApp()));

        guardar(cfg, usuarioId);
        log.info("[DOMINIO] Configuracion actualizada por {}", usuarioId);
        return toResponse(cfg);
    }

    // ── Consumido por servicios de email para construir links ─────────────────

    public String getDominioApp() {
        try {
            Map<String, Object> cfg = leerConfig();
            if (cfg == null) return defaultDominioApp;
            Object v = cfg.get("dominioApp");
            return v instanceof String s && !s.isBlank() ? s : defaultDominioApp;
        } catch (Exception e) {
            log.warn("[DOMINIO] Error leyendo dominioApp, usando default: {}", e.getMessage());
            return defaultDominioApp;
        }
    }

    public String getCorsOrigenes() {
        try {
            Map<String, Object> cfg = leerConfig();
            if (cfg == null) return defaultDominioApp;
            Object v = cfg.get("corsOrigenes");
            return v instanceof String s && !s.isBlank() ? s : defaultDominioApp;
        } catch (Exception e) {
            return defaultDominioApp;
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private ConfiguracionDominioResponse toResponse(Map<String, Object> cfg) {
        String app  = str(cfg, "dominioApp",   defaultDominioApp);
        String api  = str(cfg, "dominioApi",   app);
        String cors = str(cfg, "corsOrigenes", app);
        return new ConfiguracionDominioResponse(app, api, cors, true);
    }

    private Map<String, Object> leerConfig() {
        List<String> rows = jdbc.queryForList(
                "SELECT config::text FROM shared.configuracion_global WHERE dominio = ?",
                String.class, DOMINIO);
        if (rows.isEmpty() || rows.get(0) == null) return null;
        try {
            return mapper.readValue(rows.get(0), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[DOMINIO] Error parseando config JSONB: {}", e.getMessage());
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
            throw new RuntimeException("Error guardando config de dominio: " + e.getMessage(), e);
        }
    }

    private String normalizarCors(String corsOrigenes, String dominioApp) {
        if (corsOrigenes == null || corsOrigenes.isBlank()) return dominioApp;
        // Asegura que dominioApp esté siempre incluido
        String cors = corsOrigenes.trim();
        if (!cors.contains(dominioApp)) cors = dominioApp + "," + cors;
        return cors;
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }
}
