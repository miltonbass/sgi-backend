package com.miltonbass.sgi_backend.eventos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventoScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventoScheduler.class);

    private final JdbcTemplate jdbc;

    public EventoScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Cada 60 segundos: PROGRAMADO → ABIERTO cuando fecha_inicio ya pasó
    @Scheduled(fixedDelay = 60_000)
    public void autoAbrirEventos() {
        List<String> schemas = jdbc.queryForList(
                "SELECT schema_name FROM shared.sedes WHERE activa = TRUE AND deleted_at IS NULL",
                String.class);

        for (String schema : schemas) {
            int actualizados = jdbc.update(
                    "UPDATE " + schema + ".eventos SET estado = 'ABIERTO' "
                    + "WHERE estado = 'PROGRAMADO' AND fecha_inicio <= NOW() AND deleted_at IS NULL");
            if (actualizados > 0) {
                log.info("[EventoScheduler] {} evento(s) abierto(s) automaticamente en schema={}",
                        actualizados, schema);
            }
        }
    }
}
