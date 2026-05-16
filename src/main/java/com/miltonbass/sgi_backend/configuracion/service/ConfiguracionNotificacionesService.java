package com.miltonbass.sgi_backend.configuracion.service;

import com.miltonbass.sgi_backend.auth.entity.Sede;
import com.miltonbass.sgi_backend.auth.repository.SedeRepository;
import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ConfiguracionNotificacionesService {

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SedeRepository sedeRepo;

    public ConfiguracionNotificacionesService(SedeRepository sedeRepo) {
        this.sedeRepo = sedeRepo;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NotificacionesResponse obtener(UUID sedeId) {
        return toResponse(buscar(sedeId).getConfig());
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @Transactional
    public NotificacionesResponse actualizar(UUID sedeId, ActualizarNotificacionesRequest req) {
        validarRequest(req);

        Sede sede = buscar(sedeId);
        Map<String, Object> config = sede.getConfig() != null
                ? new HashMap<>(sede.getConfig())
                : new HashMap<>();

        config.put("notificaciones", toMap(req));
        sede.setConfig(config);
        sede.setActualizadoEn(Instant.now());
        return toResponse(sedeRepo.save(sede).getConfig());
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private Sede buscar(UUID sedeId) {
        return sedeRepo.findById(sedeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada"));
    }

    private void validarRequest(ActualizarNotificacionesRequest req) {
        validarCc(req.nuevoMiembro(),      "nuevoMiembro");
        validarCc(req.cambioEstado(),      "cambioEstado");
        validarCc(req.alertaSeguimiento(), "alertaSeguimiento");
        validarCc(req.nuevoUsuario(),      "nuevoUsuario");
    }

    private void validarCc(ConfiguracionNotificacion cfg, String campo) {
        if (cfg == null || cfg.cc() == null || cfg.cc().isBlank()) return;
        for (String email : cfg.cc().split(",")) {
            String trimmed = email.trim();
            if (!trimmed.isEmpty() && !EMAIL_RE.matcher(trimmed).matches()) {
                throw new IllegalArgumentException(
                        "Email inválido en " + campo + ".cc: '" + trimmed + "'");
            }
        }
    }

    private NotificacionesResponse toResponse(Map<String, Object> config) {
        if (config == null) return defaults();
        Object obj = config.get("notificaciones");
        if (!(obj instanceof Map<?, ?> m)) return defaults();

        return new NotificacionesResponse(
                parseCfg(m, "nuevoMiembro",      true),
                parseCfg(m, "cambioEstado",      false),
                parseCfg(m, "alertaSeguimiento", false),
                parseCfg(m, "nuevoUsuario",      false));
    }

    private NotificacionesResponse defaults() {
        return new NotificacionesResponse(
                new ConfiguracionNotificacion(true,  ""),
                new ConfiguracionNotificacion(false, ""),
                new ConfiguracionNotificacion(false, ""),
                new ConfiguracionNotificacion(false, ""));
    }

    private ConfiguracionNotificacion parseCfg(Map<?, ?> notifMap, String key, boolean defaultActivo) {
        Object val = notifMap.get(key);
        if (!(val instanceof Map<?, ?> m)) return new ConfiguracionNotificacion(defaultActivo, "");
        boolean activo = Boolean.TRUE.equals(m.get("activo"));
        String  cc     = m.get("cc") instanceof String s ? s : "";
        return new ConfiguracionNotificacion(activo, cc);
    }

    private Map<String, Object> toMap(ActualizarNotificacionesRequest req) {
        Map<String, Object> m = new HashMap<>();
        m.put("nuevoMiembro",      cfgToMap(req.nuevoMiembro()));
        m.put("cambioEstado",      cfgToMap(req.cambioEstado()));
        m.put("alertaSeguimiento", cfgToMap(req.alertaSeguimiento()));
        m.put("nuevoUsuario",      cfgToMap(req.nuevoUsuario()));
        return m;
    }

    private Map<String, Object> cfgToMap(ConfiguracionNotificacion cfg) {
        if (cfg == null) return Map.of("activo", false, "cc", "");
        Map<String, Object> m = new HashMap<>();
        m.put("activo", cfg.activo());
        m.put("cc",     cfg.cc() != null ? cfg.cc().strip() : "");
        return m;
    }
}
