package com.miltonbass.sgi_backend.reportes.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.reportes.dto.CrecimientoRetencionDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CrecimientoRetencionService {

    private static final String ESTADOS_INACTIVOS =
            "'INACTIVO','RETIRADO','TRANSFERIDO','FALLECIDO'";

    private final JdbcTemplate jdbc;

    public CrecimientoRetencionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public CrecimientoRetencionResponse obtener(UUID sedeId, int meses, boolean esAdminGlobal) {
        List<SchemaInfo> schemas = resolverSchemas(sedeId, esAdminGlobal);

        UUID respSedeId = null;
        String respSedeNombre = null;
        if (schemas.size() == 1) {
            respSedeId    = schemas.get(0).id();
            respSedeNombre = schemas.get(0).nombre();
        }

        LocalDate hoy = LocalDate.now();
        LocalDate primerMes = hoy.minusMonths(meses - 1).withDayOfMonth(1);
        LocalDate limiteHasta = hoy.withDayOfMonth(1).plusMonths(1);

        List<String> mesesSerie = generarSerieMeses(primerMes, hoy);

        Map<String, Integer> altasPorMes = new HashMap<>();
        Map<String, int[]> desglosesPorMes = new HashMap<>();
        int totalActual = 0;

        for (SchemaInfo s : schemas) {
            acumularAltas(s.schema(), primerMes, limiteHasta, altasPorMes);
            acumularBajas(s.schema(), primerMes, limiteHasta, desglosesPorMes);
            totalActual += contarActivos(s.schema());
        }

        List<PeriodoRetencion> periodos = reconstruir(mesesSerie, altasPorMes, desglosesPorMes, totalActual);

        return new CrecimientoRetencionResponse(periodos, meses, respSedeId, respSedeNombre);
    }

    // ── Reconstrucción mes a mes ──────────────────────────────────────────────

    private List<PeriodoRetencion> reconstruir(List<String> mesesSerie,
                                               Map<String, Integer> altasPorMes,
                                               Map<String, int[]> desglosesPorMes,
                                               int totalActual) {
        int N = mesesSerie.size();
        int[] fin = new int[N];
        int[] inicio = new int[N];

        fin[N - 1] = totalActual;

        for (int i = N - 1; i >= 0; i--) {
            String mes = mesesSerie.get(i);
            int a = altasPorMes.getOrDefault(mes, 0);
            int[] d = desglosesPorMes.getOrDefault(mes, new int[]{0, 0, 0});
            int b = d[0] + d[1] + d[2];

            inicio[i] = Math.max(0, fin[i] - a + b);
            if (i > 0) fin[i - 1] = inicio[i];
        }

        DateTimeFormatter fmtLabel = DateTimeFormatter.ofPattern("MMM yyyy",
                Locale.forLanguageTag("es"));
        List<PeriodoRetencion> result = new ArrayList<>(N);

        for (int i = 0; i < N; i++) {
            String mes = mesesSerie.get(i);
            int a = altasPorMes.getOrDefault(mes, 0);
            int[] d = desglosesPorMes.getOrDefault(mes, new int[]{0, 0, 0});
            int b = d[0] + d[1] + d[2];

            Double tasaRetencion = null;
            if (inicio[i] > 0) {
                double retenidos = Math.max(0, inicio[i] - b);
                tasaRetencion = Math.round(retenidos * 1000.0 / inicio[i]) / 10.0;
            }

            LocalDate fecha = LocalDate.parse(mes + "-01");
            String raw = fecha.format(fmtLabel);
            String label = raw.substring(0, 1).toUpperCase() + raw.substring(1);

            result.add(new PeriodoRetencion(
                    mes, label,
                    inicio[i], fin[i], a, b,
                    tasaRetencion,
                    new DesgloseBajas(d[0], d[1], d[2])));
        }

        return result;
    }

    // ── Consultas SQL ─────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private void acumularAltas(String schema, LocalDate desde, LocalDate hasta,
                                Map<String, Integer> result) {
        String sql = """
                SELECT TO_CHAR(DATE_TRUNC('month', COALESCE(fecha_ingreso, created_at::date)), 'YYYY-MM') AS mes,
                       COUNT(*)::int AS total
                FROM %s.miembros
                WHERE COALESCE(fecha_ingreso, created_at::date) >= ?
                  AND COALESCE(fecha_ingreso, created_at::date) < ?
                GROUP BY 1
                """.formatted(schema);

        jdbc.query(sql, rs -> {
            result.merge(rs.getString("mes"), rs.getInt("total"), Integer::sum);
        }, Date.valueOf(desde), Date.valueOf(hasta));
    }

    @SuppressWarnings("null")
    private void acumularBajas(String schema, LocalDate desde, LocalDate hasta,
                                Map<String, int[]> result) {
        String sql = """
                SELECT TO_CHAR(DATE_TRUNC('month', h.cambiado_en), 'YYYY-MM') AS mes,
                       h.estado_nuevo AS tipo,
                       COUNT(*)::int AS total
                FROM %s.miembro_estado_historial h
                WHERE h.estado_nuevo IN ('INACTIVO', 'TRANSFERIDO')
                  AND h.cambiado_en >= ? AND h.cambiado_en < ?
                GROUP BY 1, 2
                UNION ALL
                SELECT TO_CHAR(DATE_TRUNC('month', deleted_at), 'YYYY-MM') AS mes,
                       'SIN_CONTACTO' AS tipo,
                       COUNT(*)::int AS total
                FROM %s.miembros
                WHERE deleted_at IS NOT NULL AND deleted_at >= ? AND deleted_at < ?
                GROUP BY 1
                """.formatted(schema, schema);

        Date dDesde = Date.valueOf(desde);
        Date dHasta = Date.valueOf(hasta);

        jdbc.query(sql, rs -> {
            String mes = rs.getString("mes");
            String tipo = rs.getString("tipo");
            int total = rs.getInt("total");
            int[] arr = result.computeIfAbsent(mes, k -> new int[]{0, 0, 0});
            switch (tipo) {
                case "INACTIVO"    -> arr[0] += total;
                case "TRANSFERIDO" -> arr[1] += total;
                default            -> arr[2] += total;
            }
        }, dDesde, dHasta, dDesde, dHasta);
    }

    private int contarActivos(String schema) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM " + schema + ".miembros "
                + "WHERE estado NOT IN (" + ESTADOS_INACTIVOS + ") AND deleted_at IS NULL",
                Integer.class);
        return n != null ? n : 0;
    }

    // ── Resolución de schemas ─────────────────────────────────────────────────

    private List<SchemaInfo> resolverSchemas(UUID sedeId, boolean esAdminGlobal) {
        if (esAdminGlobal && sedeId != null) {
            return jdbc.query(
                    "SELECT id, nombre, schema_name FROM shared.sedes "
                    + "WHERE id = ? AND deleted_at IS NULL",
                    (rs, i) -> new SchemaInfo(
                            rs.getObject("id", UUID.class),
                            rs.getString("nombre"),
                            rs.getString("schema_name")),
                    sedeId);
        }
        if (esAdminGlobal) {
            return jdbc.query(
                    "SELECT id, nombre, schema_name FROM shared.sedes "
                    + "WHERE activa = TRUE AND deleted_at IS NULL ORDER BY nombre",
                    (rs, i) -> new SchemaInfo(
                            rs.getObject("id", UUID.class),
                            rs.getString("nombre"),
                            rs.getString("schema_name")));
        }
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.equals("shared"))
            throw new IllegalStateException("No hay tenant activo");
        return jdbc.query(
                "SELECT id, nombre, schema_name FROM shared.sedes "
                + "WHERE schema_name = ? AND deleted_at IS NULL",
                (rs, i) -> new SchemaInfo(
                        rs.getObject("id", UUID.class),
                        rs.getString("nombre"),
                        rs.getString("schema_name")),
                tenant);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> generarSerieMeses(LocalDate desde, LocalDate hasta) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        List<String> list = new ArrayList<>();
        LocalDate cursor = desde.withDayOfMonth(1);
        LocalDate tope = hasta.withDayOfMonth(1);
        while (!cursor.isAfter(tope)) {
            list.add(cursor.format(fmt));
            cursor = cursor.plusMonths(1);
        }
        return list;
    }

    private record SchemaInfo(UUID id, String nombre, String schema) {}
}
