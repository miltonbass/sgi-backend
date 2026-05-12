package com.miltonbass.sgi_backend.miembros.service;

import com.miltonbass.sgi_backend.config.TenantContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExportacionMiembrosService {

    private static final List<String> ROLES_ADMIN =
            List.of("ADMIN_SEDE", "ADMIN_GLOBAL", "SUPER_ADMIN");

    private static final DateTimeFormatter FMT_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;

    public ExportacionMiembrosService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── API pública ───────────────────────────────────────────────────────

    public byte[] exportar(String formato, String estado, UUID grupoId,
                           LocalDate fechaDesde, LocalDate fechaHasta,
                           List<String> roles) {
        String tenant = tenant();
        boolean esAdmin = roles.stream().anyMatch(ROLES_ADMIN::contains);
        List<FilaMiembro> filas = consultarMiembros(tenant, estado, grupoId, fechaDesde, fechaHasta);

        try {
            return "PDF".equalsIgnoreCase(formato)
                    ? generarPdf(filas, esAdmin)
                    : generarExcel(filas, esAdmin);
        } catch (Exception e) {
            throw new RuntimeException("Error generando exportación: " + e.getMessage(), e);
        }
    }

    // ── Consulta ──────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private List<FilaMiembro> consultarMiembros(String tenant, String estado,
                                                 UUID grupoId,
                                                 LocalDate fechaDesde,
                                                 LocalDate fechaHasta) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.numero_miembro, m.nombres, m.apellidos, m.cedula,
                       m.genero, m.estado_civil, m.telefono, m.email,
                       m.direccion, m.ciudad, m.foto_url, m.estado,
                       m.fecha_nacimiento, m.fecha_ingreso, m.fecha_bautismo,
                       g.nombre AS grupo_nombre
                FROM %s.miembros m
                LEFT JOIN %s.grupos g ON g.id = m.grupo_id
                WHERE m.deleted_at IS NULL
                """.formatted(tenant, tenant));

        List<Object> params = new ArrayList<>();

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND m.estado = ?");
            params.add(estado);
        }
        if (grupoId != null) {
            sql.append(" AND m.grupo_id = ?");
            params.add(grupoId);
        }
        if (fechaDesde != null) {
            sql.append(" AND m.fecha_ingreso >= ?");
            params.add(Date.valueOf(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND m.fecha_ingreso <= ?");
            params.add(Date.valueOf(fechaHasta));
        }

        sql.append(" ORDER BY m.apellidos, m.nombres");

        return jdbc.query(sql.toString(), (rs, i) -> new FilaMiembro(
                rs.getString("numero_miembro"),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("cedula"),
                rs.getString("genero"),
                rs.getString("estado_civil"),
                rs.getString("telefono"),
                rs.getString("email"),
                rs.getString("direccion"),
                rs.getString("ciudad"),
                rs.getString("foto_url"),
                rs.getString("estado"),
                toLocalDate(rs.getDate("fecha_nacimiento")),
                toLocalDate(rs.getDate("fecha_ingreso")),
                toLocalDate(rs.getDate("fecha_bautismo")),
                rs.getString("grupo_nombre")
        ), params.toArray());
    }

    // ── Excel ─────────────────────────────────────────────────────────────

    private byte[] generarExcel(List<FilaMiembro> filas, boolean esAdmin) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Directorio Miembros");

            XSSFCellStyle headerStyle = crearEstiloHeader(wb);
            XSSFCellStyle dataStyle   = crearEstiloDato(wb);
            XSSFCellStyle altStyle    = crearEstiloAlt(wb);

            List<String> headers = construirHeaders(esAdmin);

            // Fila de encabezados
            XSSFRow headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(20);
            for (int i = 0; i < headers.size(); i++) {
                XSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Filas de datos
            int rowNum = 1;
            for (FilaMiembro f : filas) {
                XSSFRow row = sheet.createRow(rowNum);
                XSSFCellStyle style = (rowNum % 2 == 0) ? altStyle : dataStyle;
                List<String> vals = construirValores(f, esAdmin);
                for (int i = 0; i < vals.size(); i++) {
                    XSSFCell cell = row.createCell(i);
                    cell.setCellValue(vals.get(i) != null ? vals.get(i) : "");
                    cell.setCellStyle(style);
                }
                rowNum++;
            }

            // Autoajustar columnas
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                // Limitar ancho máximo a 50 caracteres
                if (sheet.getColumnWidth(i) > 12000) sheet.setColumnWidth(i, 12000);
            }

            // Freeze header
            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private List<String> construirHeaders(boolean esAdmin) {
        List<String> h = new ArrayList<>(List.of(
                "N° Miembro", "Apellidos", "Nombres", "Género",
                "Estado", "Teléfono", "Email", "Ciudad",
                "Fecha Ingreso", "Grupo"
        ));
        if (esAdmin) {
            h.add(2, "Cédula");
            h.add("Fecha Nacimiento");
            h.add("Estado Civil");
            h.add("Dirección");
        }
        return h;
    }

    private List<String> construirValores(FilaMiembro f, boolean esAdmin) {
        List<String> v = new ArrayList<>(List.of(
                nv(f.numeroMiembro()),
                nv(f.apellidos()),
                nv(f.nombres()),
                generoLabel(f.genero()),
                nv(f.estado()),
                nv(f.telefono()),
                nv(f.email()),
                nv(f.ciudad()),
                fmt(f.fechaIngreso()),
                nv(f.grupoNombre())
        ));
        if (esAdmin) {
            v.add(2, nv(f.cedula()));
            v.add(fmt(f.fechaNacimiento()));
            v.add(nv(f.estadoCivil()));
            v.add(nv(f.direccion()));
        }
        return v;
    }

    private XSSFCellStyle crearEstiloHeader(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 33, (byte) 86, (byte) 138}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private XSSFCellStyle crearEstiloDato(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private XSSFCellStyle crearEstiloAlt(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 235, (byte) 241, (byte) 249}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private void setBorder(XSSFCellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    // ── PDF ───────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private byte[] generarPdf(List<FilaMiembro> filas, boolean esAdmin) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 36, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();

        // Título
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16,
                new Color(33, 86, 138));
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9,
                new Color(100, 100, 100));

        Paragraph titulo = new Paragraph("Directorio de Miembros", titleFont);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);

        Paragraph subtitulo = new Paragraph(
                "Exportado el " + LocalDate.now().format(FMT_FECHA)
                + " — Total: " + filas.size() + " miembro(s)", subtitleFont);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(12);
        doc.add(subtitulo);

        // Columnas: foto | apellidos+nombres | estado | teléfono | email | ciudad | fechaIngreso | grupo [+cédula]
        int numCols = esAdmin ? 9 : 8;
        float[] widths = esAdmin
                ? new float[]{1f, 3f, 1.2f, 1.8f, 2.8f, 1.5f, 1.5f, 1.8f, 1.8f}
                : new float[]{1f, 3f, 1.2f, 1.8f, 2.8f, 1.5f, 1.5f, 1.8f};

        PdfPTable table = new PdfPTable(numCols);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setHeaderRows(1);

        // Encabezados
        Color headerBg = new Color(33, 86, 138);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        List<String> colHeaders = new ArrayList<>(List.of(
                "Foto", "Apellidos / Nombres", "Estado",
                "Teléfono", "Email", "Ciudad", "F. Ingreso", "Grupo"));
        if (esAdmin) colHeaders.add("Cédula");

        for (String h : colHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            table.addCell(cell);
        }

        // Filas de datos
        Font dataFont    = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font nameBold    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Color altBg      = new Color(235, 241, 249);
        Color estadoColor = new Color(33, 86, 138);

        int idx = 0;
        for (FilaMiembro f : filas) {
            Color rowBg = (idx % 2 == 1) ? altBg : Color.WHITE;

            // Celda foto
            PdfPCell fotoCell = new PdfPCell();
            fotoCell.setFixedHeight(48);
            fotoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            fotoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            fotoCell.setBackgroundColor(rowBg);
            fotoCell.setPadding(3);
            if (f.fotoUrl() != null && !f.fotoUrl().isBlank()) {
                try {
                    Image img = Image.getInstance(new URL(f.fotoUrl()));
                    img.scaleToFit(40, 40);
                    fotoCell.addElement(img);
                } catch (Exception ignored) {
                    fotoCell.addElement(new Phrase("—", dataFont));
                }
            } else {
                fotoCell.addElement(new Phrase("—", dataFont));
            }
            table.addCell(fotoCell);

            // Nombre completo
            Paragraph nombre = new Paragraph(f.apellidos() + ", " + f.nombres(), nameBold);
            table.addCell(buildCell(nombre, rowBg));

            // Estado con color
            Font estadoFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, estadoColor);
            table.addCell(buildCell(new Phrase(nv(f.estado()), estadoFont), rowBg));

            // Resto de campos
            table.addCell(buildCell(new Phrase(nv(f.telefono()), dataFont), rowBg));
            table.addCell(buildCell(new Phrase(nv(f.email()), dataFont), rowBg));
            table.addCell(buildCell(new Phrase(nv(f.ciudad()), dataFont), rowBg));
            table.addCell(buildCell(new Phrase(fmt(f.fechaIngreso()), dataFont), rowBg));
            table.addCell(buildCell(new Phrase(nv(f.grupoNombre()), dataFont), rowBg));

            if (esAdmin) {
                table.addCell(buildCell(new Phrase(nv(f.cedula()), dataFont), rowBg));
            }

            idx++;
        }

        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    private PdfPCell buildCell(Element content, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.addElement(content);
        cell.setBackgroundColor(bg);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        return cell;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String tenant() {
        String t = TenantContext.getCurrentTenant();
        if (t == null || t.equals("shared"))
            throw new IllegalStateException("No hay tenant activo");
        return t;
    }

    private static String nv(String s) { return s != null ? s : ""; }

    private static String fmt(LocalDate d) {
        return d != null ? d.format(FMT_FECHA) : "";
    }

    private static String generoLabel(String g) {
        if (g == null) return "";
        return switch (g.toUpperCase()) {
            case "M" -> "Masculino";
            case "F" -> "Femenino";
            default  -> g;
        };
    }

    private static LocalDate toLocalDate(java.sql.Date d) {
        return d != null ? d.toLocalDate() : null;
    }

    // ── Registro interno ──────────────────────────────────────────────────

    private record FilaMiembro(
            String numeroMiembro,
            String nombres,
            String apellidos,
            String cedula,
            String genero,
            String estadoCivil,
            String telefono,
            String email,
            String direccion,
            String ciudad,
            String fotoUrl,
            String estado,
            LocalDate fechaNacimiento,
            LocalDate fechaIngreso,
            LocalDate fechaBautismo,
            String grupoNombre) {}
}
