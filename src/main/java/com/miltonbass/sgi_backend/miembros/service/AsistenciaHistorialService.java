package com.miltonbass.sgi_backend.miembros.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.miembros.dto.AsistenciaHistorialDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

@Service
public class AsistenciaHistorialService {

    private final JdbcTemplate jdbc;

    public AsistenciaHistorialService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @SuppressWarnings("null")
    public AsistenciaHistorialResponse obtener(UUID miembroId, int limite) {
        String schema = tenant();

        MiembroResumen miembro = buscarMiembro(schema, miembroId);

        List<EventoAsistencia> historial = jdbc.query("""
                SELECT e.id        AS evento_id,
                       e.titulo,
                       e.tipo,
                       e.fecha_inicio::date AS fecha,
                       a.presente,
                       a.observacion
                FROM %s.asistencias a
                JOIN %s.eventos e ON e.id = a.evento_id
                WHERE a.miembro_id = ?
                  AND e.deleted_at IS NULL
                  AND e.estado IN ('CERRADO','ABIERTO')
                ORDER BY e.fecha_inicio DESC
                LIMIT ?
                """.formatted(schema, schema),
                (rs, i) -> new EventoAsistencia(
                        rs.getObject("evento_id", UUID.class),
                        rs.getString("titulo"),
                        rs.getString("tipo"),
                        rs.getObject("fecha", Date.class).toLocalDate(),
                        rs.getBoolean("presente"),
                        rs.getString("observacion")),
                miembroId, limite);

        ResumenAsistencia resumen = calcularResumen(historial);

        return new AsistenciaHistorialResponse(miembro, resumen, historial);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MiembroResumen buscarMiembro(String schema, UUID miembroId) {
        List<MiembroResumen> result = jdbc.query(
                "SELECT id, nombres, apellidos FROM " + schema + ".miembros "
                + "WHERE id = ? AND deleted_at IS NULL",
                (rs, i) -> new MiembroResumen(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombres"),
                        rs.getString("apellidos")),
                miembroId);

        if (result.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Miembro no encontrado");
        return result.get(0);
    }

    private ResumenAsistencia calcularResumen(List<EventoAsistencia> historial) {
        int total = historial.size();
        if (total == 0) {
            return new ResumenAsistencia(0, 0, 0, null, 0, "Sin historial de asistencia");
        }

        int presentes = (int) historial.stream().filter(EventoAsistencia::presente).count();
        int ausentes  = total - presentes;
        double porcentaje = Math.round(presentes * 1000.0 / total) / 10.0;

        // Racha: recorre desde el evento más reciente hasta que cambia el patrón
        int racha = 0;
        boolean patron = historial.get(0).presente();
        for (EventoAsistencia e : historial) {
            if (e.presente() == patron) {
                racha++;
            } else {
                break;
            }
        }
        if (!patron) racha = -racha;

        String rachaLabel = construirRachaLabel(racha);

        return new ResumenAsistencia(total, presentes, ausentes, porcentaje, racha, rachaLabel);
    }

    private String construirRachaLabel(int racha) {
        if (racha == 0) return "Sin racha definida";
        int abs = Math.abs(racha);
        return racha > 0
                ? abs + " asistencia" + (abs > 1 ? "s" : "") + " consecutiva" + (abs > 1 ? "s" : "")
                : abs + " ausencia" + (abs > 1 ? "s" : "") + " consecutiva" + (abs > 1 ? "s" : "");
    }

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared"))
            throw new IllegalStateException("No hay tenant activo");
        return t;
    }
}
