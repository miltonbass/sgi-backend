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
import java.util.Set;
import java.util.UUID;

@Service
public class ConfiguracionParametrosService {

    private static final Set<Integer> PERIODOS_VALIDOS = Set.of(30, 60, 90);

    private final SedeRepository sedeRepo;

    public ConfiguracionParametrosService(SedeRepository sedeRepo) {
        this.sedeRepo = sedeRepo;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ParametrosResponse obtener(UUID sedeId) {
        return toResponse(buscar(sedeId).getConfig());
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @Transactional
    public ParametrosResponse actualizar(UUID sedeId, ActualizarParametrosRequest req) {
        if (!PERIODOS_VALIDOS.contains(req.periodoReportesDias())) {
            throw new IllegalArgumentException("periodoReportesDias debe ser 30, 60 o 90");
        }

        Sede sede = buscar(sedeId);
        Map<String, Object> config = sede.getConfig() != null
                ? new HashMap<>(sede.getConfig())
                : new HashMap<>();

        config.put("parametros", toMap(req));
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

    private ParametrosResponse toResponse(Map<String, Object> config) {
        if (config == null) return defaults();
        Object obj = config.get("parametros");
        if (!(obj instanceof Map<?, ?> m)) return defaults();

        return new ParametrosResponse(
                getStr(m, "estadoInicialMiembro", "VISITOR"),
                getInt(m, "diasSinContactoAlerta", 30),
                getStr(m, "moneda",                "COP"),
                getInt(m, "periodoReportesDias",   30),
                getInt(m, "limiteMiembrosCelula",  0));
    }

    private ParametrosResponse defaults() {
        return new ParametrosResponse("VISITOR", 30, "COP", 30, 0);
    }

    private String getStr(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

    private int getInt(Map<?, ?> m, String key, int def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    private Map<String, Object> toMap(ActualizarParametrosRequest req) {
        Map<String, Object> m = new HashMap<>();
        m.put("estadoInicialMiembro", req.estadoInicialMiembro());
        m.put("diasSinContactoAlerta", req.diasSinContactoAlerta());
        m.put("moneda",                req.moneda());
        m.put("periodoReportesDias",   req.periodoReportesDias());
        m.put("limiteMiembrosCelula",  req.limiteMiembrosCelula());
        return m;
    }
}
