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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ConfiguracionPlantillasService {

    private static final List<String> VARIABLES_COMUNES = List.of("nombre", "iglesia", "link", "fecha");

    private static final Map<String, List<String>> VARS_REQUERIDAS = Map.of(
            "bienvenida",        List.of("nombre", "iglesia"),
            "activacion",        List.of("nombre", "link"),
            "reset-password",    List.of("nombre", "link"),
            "alerta-seguimiento",List.of("nombre", "iglesia")
    );

    private static final Set<String> TIPOS_VALIDOS = VARS_REQUERIDAS.keySet();

    private final SedeRepository sedeRepo;

    public ConfiguracionPlantillasService(SedeRepository sedeRepo) {
        this.sedeRepo = sedeRepo;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlantillaCorreoResponse obtener(UUID sedeId, String tipo) {
        validarTipo(tipo);
        Map<?, ?> guardada = leerPlantilla(buscar(sedeId).getConfig(), tipo);
        if (guardada == null) {
            return defaultResponse(tipo);
        }
        return new PlantillaCorreoResponse(
                tipo,
                str(guardada, "asunto", defaultAsunto(tipo)),
                str(guardada, "cuerpo",  defaultCuerpo(tipo)),
                VARIABLES_COMUNES,
                true);
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @Transactional
    public PlantillaCorreoResponse actualizar(UUID sedeId, String tipo, ActualizarPlantillaRequest req) {
        validarTipo(tipo);
        validarVariables(tipo, req.asunto(), req.cuerpo());

        Sede sede = buscar(sedeId);
        Map<String, Object> config = sede.getConfig() != null
                ? new HashMap<>(sede.getConfig())
                : new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> plantillas = config.get("plantillas") instanceof Map<?, ?> m
                ? new HashMap<>((Map<String, Object>) m)
                : new HashMap<>();

        Map<String, Object> entry = new HashMap<>();
        entry.put("asunto", req.asunto());
        entry.put("cuerpo",  req.cuerpo());
        plantillas.put(tipo, entry);

        config.put("plantillas", plantillas);
        sede.setConfig(config);
        sede.setActualizadoEn(Instant.now());
        sedeRepo.save(sede);

        return new PlantillaCorreoResponse(tipo, req.asunto(), req.cuerpo(), VARIABLES_COMUNES, true);
    }

    // ── DELETE (restaurar por defecto) ────────────────────────────────────────

    @Transactional
    public PlantillaCorreoResponse restaurar(UUID sedeId, String tipo) {
        validarTipo(tipo);

        Sede sede = buscar(sedeId);
        Map<String, Object> config = sede.getConfig() != null
                ? new HashMap<>(sede.getConfig())
                : new HashMap<>();

        if (config.get("plantillas") instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> plantillas = new HashMap<>((Map<String, Object>) m);
            plantillas.remove(tipo);
            config.put("plantillas", plantillas);
            sede.setConfig(config);
            sede.setActualizadoEn(Instant.now());
            sedeRepo.save(sede);
        }

        return defaultResponse(tipo);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private Sede buscar(UUID sedeId) {
        return sedeRepo.findById(sedeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada"));
    }

    private void validarTipo(String tipo) {
        if (!TIPOS_VALIDOS.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de plantilla inválido: '" + tipo + "'. Valores válidos: " + TIPOS_VALIDOS);
        }
    }

    private void validarVariables(String tipo, String asunto, String cuerpo) {
        String texto = asunto + " " + cuerpo;
        List<String> requeridas = VARS_REQUERIDAS.get(tipo);
        List<String> faltantes = requeridas.stream()
                .filter(v -> !texto.contains("{{" + v + "}}"))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Faltan variables obligatorias para '" + tipo + "': "
                    + faltantes.stream().map(v -> "{{" + v + "}}").toList());
        }
    }

    private Map<?, ?> leerPlantilla(Map<String, Object> config, String tipo) {
        if (config == null) return null;
        Object plantillas = config.get("plantillas");
        if (!(plantillas instanceof Map<?, ?> m)) return null;
        Object entry = m.get(tipo);
        return entry instanceof Map<?, ?> p ? p : null;
    }

    private PlantillaCorreoResponse defaultResponse(String tipo) {
        return new PlantillaCorreoResponse(tipo, defaultAsunto(tipo), defaultCuerpo(tipo),
                VARIABLES_COMUNES, false);
    }

    private String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

    private String defaultAsunto(String tipo) {
        return switch (tipo) {
            case "bienvenida"         -> "Bienvenido a {{iglesia}}, {{nombre}}";
            case "activacion"         -> "Activa tu cuenta en {{iglesia}}";
            case "reset-password"     -> "Restablece tu contraseña — {{iglesia}}";
            case "alerta-seguimiento" -> "Alerta de seguimiento — {{nombre}}";
            default -> "";
        };
    }

    private String defaultCuerpo(String tipo) {
        return switch (tipo) {
            case "bienvenida" -> """
                    Hola {{nombre}},

                    Tu acceso al sistema de gestión de {{iglesia}} ha sido creado exitosamente.

                    Fecha de registro: {{fecha}}
                    Enlace de acceso: {{link}}

                    Estamos felices de tenerte con nosotros.

                    Equipo de {{iglesia}}""";
            case "activacion" -> """
                    Hola {{nombre}},

                    Haz clic en el siguiente enlace para activar tu cuenta:

                    {{link}}

                    Si no solicitaste este acceso, ignora este correo.

                    Equipo de {{iglesia}}""";
            case "reset-password" -> """
                    Hola {{nombre}},

                    Recibimos una solicitud para restablecer tu contraseña en {{iglesia}}.

                    Haz clic en el siguiente enlace para continuar:

                    {{link}}

                    Este enlace expirará en 24 horas. Si no solicitaste esto, ignora este correo.

                    Equipo de {{iglesia}}""";
            case "alerta-seguimiento" -> """
                    Estimado equipo,

                    El miembro {{nombre}} de {{iglesia}} no ha tenido contacto registrado recientemente.

                    Fecha de alerta: {{fecha}}

                    Te recordamos hacer seguimiento pastoral a este miembro.

                    Equipo de {{iglesia}}""";
            default -> "";
        };
    }
}
