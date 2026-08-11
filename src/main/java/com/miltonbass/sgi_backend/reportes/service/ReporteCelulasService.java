package com.miltonbass.sgi_backend.reportes.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.reportes.dto.ReporteCelulasDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReporteCelulasService {

    private final JdbcTemplate jdbc;

    public ReporteCelulasService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Vista árbol con métricas ──────────────────────────────────────────────

    public ReporteCelulasResponse obtenerArbol(LocalDate fechaDesde, LocalDate fechaHasta,
                                               Integer nivel, boolean soloActivas) {
        String t = tenant();

        String activoWhere = soloActivas ? "AND ar.activo = TRUE" : "";
        String nivelWhere  = nivel != null ? "AND ar.nivel = ?" : "";

        String sql =
            "WITH RECURSIVE arbol AS ("
            + "  SELECT g.id, g.nombre, g.tipo, g.lider_id, g.grupo_padre_id, g.activo, 0 AS nivel"
            + "  FROM " + t + ".grupos g WHERE g.grupo_padre_id IS NULL"
            + (soloActivas ? " AND g.activo = TRUE" : "")
            + "  UNION ALL"
            + "  SELECT g.id, g.nombre, g.tipo, g.lider_id, g.grupo_padre_id, g.activo, ar.nivel + 1"
            + "  FROM " + t + ".grupos g"
            + "  JOIN arbol ar ON g.grupo_padre_id = ar.id"
            + (soloActivas ? " WHERE g.activo = TRUE" : "")
            + "),"
            + "sesion_stats AS ("
            + "  SELECT sg.grupo_id, sg.id AS sesion_id,"
            + "    COUNT(*) FILTER (WHERE a.presente = TRUE AND a.miembro_id IS NOT NULL) AS presentes,"
            + "    COUNT(*) FILTER (WHERE a.presente = TRUE AND a.visitante_nombre IS NOT NULL) AS visitantes"
            + "  FROM " + t + ".sesiones_grupo sg"
            + "  LEFT JOIN " + t + ".asistencias_sesion a ON a.sesion_id = sg.id"
            + "  WHERE sg.fecha BETWEEN ? AND ?"
            + "  GROUP BY sg.grupo_id, sg.id"
            + "),"
            + "grupo_stats AS ("
            + "  SELECT ss.grupo_id,"
            + "    COUNT(DISTINCT ss.sesion_id)   AS total_sesiones,"
            + "    AVG(ss.presentes)              AS promedio_asistencia,"
            + "    SUM(ss.visitantes)             AS total_visitantes"
            + "  FROM sesion_stats ss GROUP BY ss.grupo_id"
            + "),"
            + "grupo_ofrenda AS ("
            + "  SELECT sg.grupo_id, COALESCE(SUM(sg.ofrenda_monto), 0) AS total_ofrenda"
            + "  FROM " + t + ".sesiones_grupo sg"
            + "  WHERE sg.fecha BETWEEN ? AND ?"
            + "  GROUP BY sg.grupo_id"
            + ")"
            + "SELECT ar.id, ar.nombre, ar.tipo, ar.lider_id, ar.grupo_padre_id, ar.nivel, ar.activo,"
            + "  (SELECT m.nombres || ' ' || m.apellidos FROM " + t + ".miembros m WHERE m.id = ar.lider_id) AS lider_nombre,"
            + "  (SELECT COUNT(*) FROM " + t + ".miembro_grupos mg WHERE mg.grupo_id = ar.id) AS total_miembros,"
            + "  COALESCE(gs.total_sesiones, 0)    AS total_sesiones,"
            + "  gs.promedio_asistencia,"
            + "  COALESCE(gs.total_visitantes, 0)  AS total_visitantes,"
            + "  COALESCE(go.total_ofrenda, 0)     AS total_ofrenda"
            + " FROM arbol ar"
            + " LEFT JOIN grupo_stats gs ON gs.grupo_id = ar.id"
            + " LEFT JOIN grupo_ofrenda go ON go.grupo_id = ar.id"
            + " WHERE 1=1 " + activoWhere + " " + nivelWhere
            + " ORDER BY ar.nivel ASC, ar.nombre ASC";

        List<Object> params = new ArrayList<>();
        params.add(Date.valueOf(fechaDesde));
        params.add(Date.valueOf(fechaHasta));
        params.add(Date.valueOf(fechaDesde));
        params.add(Date.valueOf(fechaHasta));
        if (nivel != null) params.add(nivel);

        List<CelulaReporteItem> celulas = jdbc.query(sql, (rs, i) -> {
            Object liderIdObj   = rs.getObject("lider_id");
            Object padreIdObj   = rs.getObject("grupo_padre_id");
            Object promObj      = rs.getObject("promedio_asistencia");
            return new CelulaReporteItem(
                    rs.getObject("id", UUID.class),
                    rs.getString("nombre"),
                    rs.getString("tipo"),
                    liderIdObj != null ? UUID.fromString(liderIdObj.toString()) : null,
                    rs.getString("lider_nombre"),
                    padreIdObj != null ? UUID.fromString(padreIdObj.toString()) : null,
                    rs.getInt("nivel"),
                    rs.getBoolean("activo"),
                    rs.getInt("total_miembros"),
                    rs.getLong("total_sesiones"),
                    promObj != null ? ((Number) promObj).doubleValue() : null,
                    rs.getLong("total_visitantes"),
                    rs.getBigDecimal("total_ofrenda"));
        }, params.toArray());

        long totalMiembros = celulas.stream().mapToLong(c -> c.totalMiembros()).sum();
        BigDecimal totalOfrenda = celulas.stream()
                .filter(c -> c.totalOfrenda() != null)
                .map(c -> c.totalOfrenda())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        Double promGeneral = celulas.stream()
                .filter(c -> c.promedioAsistencia() != null)
                .mapToDouble(c -> c.promedioAsistencia())
                .average().stream().boxed().findFirst().orElse(null);

        return new ReporteCelulasResponse(
                celulas, fechaDesde, fechaHasta,
                celulas.size(), totalMiembros, totalOfrenda, promGeneral);
    }

    // ── Detalle de célula ─────────────────────────────────────────────────────

    public DetalleCelulaResponse obtenerDetalle(UUID grupoId,
                                                LocalDate fechaDesde,
                                                LocalDate fechaHasta) {
        String t = tenant();

        // Info del grupo
        String infoSql =
            "WITH RECURSIVE arbol AS ("
            + "  SELECT g.id, 0 AS nivel FROM " + t + ".grupos g WHERE g.grupo_padre_id IS NULL"
            + "  UNION ALL"
            + "  SELECT g.id, ar.nivel + 1 FROM " + t + ".grupos g"
            + "  JOIN arbol ar ON g.grupo_padre_id = ar.id"
            + ")"
            + "SELECT g.nombre, g.tipo, g.lider_id, ar.nivel,"
            + "  (SELECT m.nombres || ' ' || m.apellidos FROM " + t + ".miembros m WHERE m.id = g.lider_id) AS lider_nombre,"
            + "  (SELECT COUNT(*) FROM " + t + ".miembro_grupos mg WHERE mg.grupo_id = g.id) AS total_miembros"
            + " FROM " + t + ".grupos g"
            + " LEFT JOIN arbol ar ON ar.id = g.id"
            + " WHERE g.id = ?";

        record GrupoInfo(String nombre, String tipo, UUID liderId, String liderNombre, int nivel, int totalMiembros) {}
        GrupoInfo info = jdbc.query(infoSql, (rs, i) -> {
            Object liderIdObj = rs.getObject("lider_id");
            return new GrupoInfo(
                    rs.getString("nombre"), rs.getString("tipo"),
                    liderIdObj != null ? UUID.fromString(liderIdObj.toString()) : null,
                    rs.getString("lider_nombre"),
                    rs.getInt("nivel"), rs.getInt("total_miembros"));
        }, grupoId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado: " + grupoId));

        // Sesiones con resumen de asistencia
        String sesionSql =
            "SELECT sg.id, sg.fecha, sg.tema, sg.lugar, sg.ofrenda_monto,"
            + "  COUNT(*) FILTER (WHERE a.presente = TRUE AND a.miembro_id IS NOT NULL) AS total_presentes,"
            + "  COUNT(*) FILTER (WHERE a.presente = TRUE AND a.visitante_nombre IS NOT NULL) AS total_visitantes"
            + " FROM " + t + ".sesiones_grupo sg"
            + " LEFT JOIN " + t + ".asistencias_sesion a ON a.sesion_id = sg.id"
            + " WHERE sg.grupo_id = ? AND sg.fecha BETWEEN ? AND ?"
            + " GROUP BY sg.id, sg.fecha, sg.tema, sg.lugar, sg.ofrenda_monto"
            + " ORDER BY sg.fecha DESC";

        List<SesionDetalle> sesiones = jdbc.query(sesionSql, (rs, i) -> {
            Date fecha = rs.getDate("fecha");
            return new SesionDetalle(
                    rs.getObject("id", UUID.class),
                    fecha != null ? fecha.toLocalDate() : null,
                    rs.getString("tema"),
                    rs.getString("lugar"),
                    rs.getInt("total_presentes"),
                    rs.getInt("total_visitantes"),
                    rs.getBigDecimal("ofrenda_monto"));
        }, grupoId, Date.valueOf(fechaDesde), Date.valueOf(fechaHasta));

        long totalSesiones = sesiones.size();

        // Miembros con % asistencia
        String miembroSql =
            "SELECT m.id, m.nombres, m.apellidos,"
            + "  (SELECT COUNT(*) FROM " + t + ".asistencias_sesion a"
            + "   JOIN " + t + ".sesiones_grupo sg ON sg.id = a.sesion_id"
            + "   WHERE sg.grupo_id = ? AND sg.fecha BETWEEN ? AND ?"
            + "   AND a.miembro_id = m.id AND a.presente = TRUE) AS sesiones_asistidas"
            + " FROM " + t + ".miembro_grupos mg"
            + " JOIN " + t + ".miembros m ON m.id = mg.miembro_id"
            + " WHERE mg.grupo_id = ?"
            + " ORDER BY m.apellidos, m.nombres";

        long ts = totalSesiones;
        List<MiembroAsistenciaItem> miembros = jdbc.query(miembroSql, (rs, i) -> {
            long asistidas = rs.getLong("sesiones_asistidas");
            double pct = ts > 0 ? Math.round((asistidas * 100.0 / ts) * 10.0) / 10.0 : 0.0;
            return new MiembroAsistenciaItem(
                    rs.getObject("id", UUID.class),
                    rs.getString("nombres"),
                    rs.getString("apellidos"),
                    asistidas, ts, pct);
        }, grupoId, Date.valueOf(fechaDesde), Date.valueOf(fechaHasta), grupoId);

        BigDecimal totalOfrenda = sesiones.stream()
                .filter(s -> s.ofrendaMonto() != null)
                .map(s -> s.ofrendaMonto())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        long totalVisitantes = sesiones.stream().mapToLong(s -> s.totalVisitantes()).sum();
        Double promedio = sesiones.stream().mapToInt(s -> s.totalPresentes())
                .average().stream().boxed().findFirst().orElse(null);

        return new DetalleCelulaResponse(
                grupoId, info.nombre(), info.tipo(),
                info.liderId(), info.liderNombre(),
                info.nivel(), info.totalMiembros(),
                fechaDesde, fechaHasta,
                sesiones, miembros,
                totalOfrenda, totalVisitantes, promedio);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared"))
            throw new IllegalStateException("No hay tenant activo");
        return t;
    }
}
