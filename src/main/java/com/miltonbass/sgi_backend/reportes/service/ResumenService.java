package com.miltonbass.sgi_backend.reportes.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.reportes.dto.ResumenDtos.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResumenService {

    private static final Logger log = LoggerFactory.getLogger(ResumenService.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Bogota"));

    private final JdbcTemplate jdbc;

    public ResumenService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Resumen del evento ────────────────────────────────────────────────────
    public EventoResumenResponse resumen(UUID eventoId) {
        String tenant = tenant();
        Map<String, Object> evento = obtenerEvento(tenant, eventoId);

        String sql = "SELECT "
                + "COUNT(*) FILTER (WHERE a.presente = TRUE)                                              AS total, "
                + "COUNT(*) FILTER (WHERE a.presente = TRUE AND a.miembro_id IS NULL)                    AS visitantes, "
                + "COUNT(*) FILTER (WHERE a.presente = TRUE AND a.miembro_id IS NOT NULL "
                + "  AND m.estado NOT IN ('INACTIVO','RETIRADO','TRANSFERIDO','FALLECIDO'))               AS activos, "
                + "COUNT(*) FILTER (WHERE a.presente = TRUE AND a.miembro_id IS NOT NULL "
                + "  AND m.estado IN ('INACTIVO','RETIRADO','TRANSFERIDO','FALLECIDO'))                   AS otros "
                + "FROM " + tenant + ".asistencias a "
                + "LEFT JOIN " + tenant + ".miembros m ON a.miembro_id = m.id "
                + "WHERE a.evento_id = ?";

        Map<String, Object> row = jdbc.queryForMap(sql, eventoId);

        int total      = toInt(row.get("total"));
        int visitantes = toInt(row.get("visitantes"));
        int activos    = toInt(row.get("activos"));
        int otros      = toInt(row.get("otros"));
        Integer capacidad = (Integer) evento.get("capacidad");
        Double pct = (capacidad != null && capacidad > 0)
                ? Math.round((total * 100.0 / capacidad) * 10) / 10.0
                : null;

        return new EventoResumenResponse(
                eventoId,
                (String) evento.get("titulo"),
                (String) evento.get("tipo"),
                (String) evento.get("estado"),
                toInstant(evento.get("fecha_inicio")),
                toInstant(evento.get("fecha_fin")),
                capacidad,
                total, pct, visitantes, activos, otros);
    }

    // ── Comparativa últimas 4 semanas ─────────────────────────────────────────
    public ComparativaResponse comparativa(UUID eventoId) {
        String tenant = tenant();
        EventoResumenResponse actual = resumen(eventoId);

        String sql = "SELECT e.id, e.titulo, e.fecha_inicio, e.capacidad, "
                + "COUNT(a.id) FILTER (WHERE a.presente = TRUE) AS total_presentes "
                + "FROM " + tenant + ".eventos e "
                + "LEFT JOIN " + tenant + ".asistencias a ON a.evento_id = e.id "
                + "WHERE e.tipo = ? "
                + "AND e.estado = 'CERRADO' "
                + "AND e.fecha_inicio >= NOW() - INTERVAL '28 days' "
                + "AND e.id != ? "
                + "AND e.deleted_at IS NULL "
                + "GROUP BY e.id, e.titulo, e.fecha_inicio, e.capacidad "
                + "ORDER BY e.fecha_inicio DESC "
                + "LIMIT 4";

        List<ComparativaItem> items = jdbc.query(sql, (rs, i) -> {
            int tp  = rs.getInt("total_presentes");
            Integer cap = (Integer) rs.getObject("capacidad");
            Double pct = (cap != null && cap > 0) ? Math.round((tp * 100.0 / cap) * 10) / 10.0 : null;
            Timestamp fi = rs.getTimestamp("fecha_inicio");
            return new ComparativaItem(
                    rs.getObject("id", UUID.class),
                    rs.getString("titulo"),
                    fi != null ? fi.toInstant() : null,
                    cap, tp, pct);
        }, actual.tipo(), eventoId);

        return new ComparativaResponse(actual, items);
    }

    // ── Exportar lista de presentes a Excel ───────────────────────────────────
    public byte[] exportarExcel(UUID eventoId) {
        String tenant = tenant();
        Map<String, Object> evento = obtenerEvento(tenant, eventoId);

        String sql = "SELECT "
                + "COALESCE(m.nombres || ' ' || m.apellidos, a.visitante_nombre) AS nombre_completo, "
                + "COALESCE(m.numero_miembro, 'Visitante')                       AS numero_miembro, "
                + "COALESCE(m.estado, 'VISITANTE')                               AS estado_miembro, "
                + "COALESCE(m.telefono, a.visitante_telefono)                    AS telefono, "
                + "a.observacion, a.created_at "
                + "FROM " + tenant + ".asistencias a "
                + "LEFT JOIN " + tenant + ".miembros m ON a.miembro_id = m.id "
                + "WHERE a.evento_id = ? AND a.presente = TRUE "
                + "ORDER BY nombre_completo ASC";

        List<PresenciaExportRow> filas = jdbc.query(sql, (rs, i) -> {
            Timestamp cat = rs.getTimestamp("created_at");
            return new PresenciaExportRow(
                    rs.getString("nombre_completo"),
                    rs.getString("numero_miembro"),
                    rs.getString("estado_miembro"),
                    rs.getString("telefono"),
                    rs.getString("observacion"),
                    cat != null ? cat.toInstant() : null);
        }, eventoId);

        return buildExcel((String) evento.get("titulo"), filas);
    }

    // ── Builder Excel ─────────────────────────────────────────────────────────
    private byte[] buildExcel(String tituloEvento, List<PresenciaExportRow> filas) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Asistencia");

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Título
            Row title = sheet.createRow(0);
            Cell tc = title.createCell(0);
            tc.setCellValue("Lista de Asistencia: " + tituloEvento);
            tc.setCellStyle(headerStyle);

            // Encabezados
            String[] cols = {"Nombre Completo", "N° Miembro", "Estado", "Telefono", "Observacion", "Hora registro"};
            Row header = sheet.createRow(2);
            for (int c = 0; c < cols.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(cols[c]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            int rowNum = 3;
            for (PresenciaExportRow f : filas) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nvl(f.nombreCompleto()));
                row.createCell(1).setCellValue(nvl(f.numeroMiembro()));
                row.createCell(2).setCellValue(nvl(f.estadoMiembro()));
                row.createCell(3).setCellValue(nvl(f.telefono()));
                row.createCell(4).setCellValue(nvl(f.observacion()));
                row.createCell(5).setCellValue(f.registradoEn() != null ? FMT.format(f.registradoEn()) : "");
            }

            // Total
            Row total = sheet.createRow(rowNum + 1);
            Cell tc2 = total.createCell(0);
            tc2.setCellValue("Total presentes: " + filas.size());
            tc2.setCellStyle(headerStyle);

            for (int c = 0; c < cols.length; c++) sheet.autoSizeColumn(c);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            log.info("[Resumen] Excel generado para evento, {} filas", filas.size());
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error generando Excel", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> obtenerEvento(String tenant, UUID eventoId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT titulo, tipo, estado, fecha_inicio, fecha_fin, capacidad "
                + "FROM " + tenant + ".eventos WHERE id = ? AND deleted_at IS NULL",
                eventoId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Evento no encontrado: " + eventoId);
        return rows.get(0);
    }

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared")) throw new IllegalStateException("No hay tenant activo");
        return t;
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        return ((Number) v).intValue();
    }

    private java.time.Instant toInstant(Object v) {
        if (v == null) return null;
        return ((Timestamp) v).toInstant();
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
