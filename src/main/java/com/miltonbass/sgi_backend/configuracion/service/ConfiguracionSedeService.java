package com.miltonbass.sgi_backend.configuracion.service;

import com.miltonbass.sgi_backend.auth.entity.Sede;
import com.miltonbass.sgi_backend.auth.repository.SedeRepository;
import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfiguracionSedeService {

    private final SedeRepository sedeRepo;

    public ConfiguracionSedeService(SedeRepository sedeRepo) {
        this.sedeRepo = sedeRepo;
    }

    @Transactional(readOnly = true)
    public ConfiguracionSedeResponse obtener(UUID sedeId) {
        return toResponse(buscar(sedeId));
    }

    @Transactional
    public ConfiguracionSedeResponse actualizar(UUID sedeId, ActualizarConfiguracionSedeRequest req) {
        Sede sede = buscar(sedeId);

        sede.setNombre(req.nombre());
        if (req.descripcion()  != null) sede.setDescripcion(req.descripcion());
        if (req.ciudad()       != null) sede.setCiudad(req.ciudad());
        if (req.departamento() != null) sede.setDepartamento(req.departamento());
        if (req.pais()         != null) sede.setPais(req.pais());
        if (req.direccion()    != null) sede.setDireccion(req.direccion());
        if (req.telefono()     != null) sede.setTelefono(req.telefono());
        if (req.email()        != null) sede.setEmail(req.email());
        if (req.fechaFundacion() != null) sede.setFechaFundacion(req.fechaFundacion());

        if (req.zonaHoraria() != null) {
            try { ZoneId.of(req.zonaHoraria()); }
            catch (DateTimeException e) {
                throw new IllegalArgumentException("Zona horaria inválida: " + req.zonaHoraria());
            }
            sede.setZonaHoraria(req.zonaHoraria());
        }

        Map<String, Object> config = sede.getConfig() != null
                ? new HashMap<>(sede.getConfig())
                : new HashMap<>();

        if (req.sitioWeb() != null) config.put("sitioWeb", req.sitioWeb());

        if (req.redesSociales() != null) {
            Map<String, String> redes = new HashMap<>();
            if (req.redesSociales().instagram() != null) redes.put("instagram", req.redesSociales().instagram());
            if (req.redesSociales().facebook()  != null) redes.put("facebook",  req.redesSociales().facebook());
            if (req.redesSociales().youtube()   != null) redes.put("youtube",   req.redesSociales().youtube());
            config.put("redesSociales", redes);
        }

        sede.setConfig(config);
        sede.setActualizadoEn(Instant.now());
        return toResponse(sedeRepo.save(sede));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private Sede buscar(UUID sedeId) {
        return sedeRepo.findById(sedeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada"));
    }

    private ConfiguracionSedeResponse toResponse(Sede s) {
        Map<String, Object> config = s.getConfig() != null ? s.getConfig() : Map.of();

        String sitioWeb = (String) config.get("sitioWeb");

        RedesSociales redesSociales = null;
        Object redesObj = config.get("redesSociales");
        if (redesObj instanceof Map<?, ?> redes) {
            redesSociales = new RedesSociales(
                    (String) redes.get("instagram"),
                    (String) redes.get("facebook"),
                    (String) redes.get("youtube"));
        }

        return new ConfiguracionSedeResponse(
                s.getId(), s.getCodigo(), s.getNombre(), s.getDescripcion(),
                s.getCiudad(), s.getDepartamento(), s.getPais(), s.getDireccion(),
                s.getTelefono(), s.getEmail(), sitioWeb, s.getZonaHoraria(),
                s.getFechaFundacion(), redesSociales, s.isActiva());
    }
}
