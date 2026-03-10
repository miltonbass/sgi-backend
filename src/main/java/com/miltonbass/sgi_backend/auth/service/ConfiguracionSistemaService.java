package com.miltonbass.sgi_backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Lee la configuración del sistema desde shared.configuracion_sistema.
 * Cachea los valores al primer uso para evitar queries repetitivas.
 *
 * Claves usadas:
 *   LOGIN_MAX_INTENTOS       → default 5
 *   LOGIN_BLOQUEO_MINUTOS    → default 15
 */
@Service
public class ConfiguracionSistemaService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionSistemaService.class);

    private final JdbcTemplate jdbc;

    // Cache simple en memoria (los valores raramente cambian)
    private volatile int maxIntentos = 5;
    private volatile long bloqueoMinutos = 15;
    private volatile boolean cargado = false;

    public ConfiguracionSistemaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int getMaxIntentos() {
        cargarSiNecesario();
        return maxIntentos;
    }

    public long getBloqueoMinutos() {
        cargarSiNecesario();
        return bloqueoMinutos;
    }

    /** Fuerza recarga de la configuración desde BD */
    public void recargar() {
        cargado = false;
        cargarSiNecesario();
    }

    private synchronized void cargarSiNecesario() {
        if (cargado) return;
        try {
            var rows = jdbc.queryForList(
                    "SELECT clave, valor FROM shared.configuracion_sistema WHERE clave IN (?, ?)",
                    "LOGIN_MAX_INTENTOS", "LOGIN_BLOQUEO_MINUTOS"
            );
            for (var row : rows) {
                String clave = (String) row.get("clave");
                String valor = (String) row.get("valor");
                if ("LOGIN_MAX_INTENTOS".equals(clave)) {
                    maxIntentos = Integer.parseInt(valor);
                } else if ("LOGIN_BLOQUEO_MINUTOS".equals(clave)) {
                    bloqueoMinutos = Long.parseLong(valor);
                }
            }
            cargado = true;
            log.info("Configuración cargada: maxIntentos={}, bloqueoMinutos={}", maxIntentos, bloqueoMinutos);
        } catch (Exception e) {
            log.warn("No se pudo cargar configuración del sistema, usando defaults. Error: {}", e.getMessage());
        }
    }
}