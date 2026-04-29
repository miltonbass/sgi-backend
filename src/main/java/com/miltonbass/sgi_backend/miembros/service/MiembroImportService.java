package com.miltonbass.sgi_backend.miembros.service;

import com.miltonbass.sgi_backend.miembros.dto.MiembroDtos.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class MiembroImportService {

    private static final Logger log = LoggerFactory.getLogger(MiembroImportService.class);

    private static final Set<String> ESTADOS_VALIDOS = Set.of("VISITOR", "MIEMBRO");

    private final JdbcTemplate jdbc;

    public MiembroImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Entrada principal ──────────────────────────────────────────────────
    public ImportMiembrosResult importar(MultipartFile archivo,
                                         String estadoDefault,
                                         UUID sedeId,
                                         UUID creadoPorId) throws Exception {
        String tenant = com.miltonbass.sgi_backend.config.TenantContext.getCurrentTenant();
        if (tenant == null || tenant.equals("shared")) {
            throw new IllegalStateException("No hay tenant activo");
        }

        String estado = estadoDefault != null ? estadoDefault.toUpperCase() : "VISITOR";
        if (!ESTADOS_VALIDOS.contains(estado)) {
            throw new IllegalArgumentException(
                    "estadoDefault inválido: " + estadoDefault + ". Valores permitidos: " + ESTADOS_VALIDOS);
        }

        String rawContentType = archivo.getContentType();
        String rawFilename    = archivo.getOriginalFilename();
        String contentType    = rawContentType != null ? rawContentType : "";
        String filename       = rawFilename    != null ? rawFilename.toLowerCase() : "";

        boolean esExcel = contentType.contains("spreadsheet") || contentType.contains("ms-excel")
                || filename.endsWith(".xlsx") || filename.endsWith(".xls");

        List<FilaData> filas = esExcel
                ? parseExcel(archivo.getInputStream())
                : parseCsv(archivo.getBytes());

        return procesarFilas(filas, tenant, estado, sedeId, creadoPorId);
    }

    // ── Parser CSV ─────────────────────────────────────────────────────────
    private List<FilaData> parseCsv(byte[] bytes) {
        // Eliminar BOM UTF-8 si está presente
        int offset = 0;
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            offset = 3;
        }
        String content = new String(bytes, offset, bytes.length - offset, java.nio.charset.StandardCharsets.UTF_8);
        String[] lineas = content.split("\r?\n");

        if (lineas.length == 0) return List.of();

        Map<String, Integer> colIdx = mapearHeaders(splitCsvLine(lineas[0]));
        List<FilaData> result = new ArrayList<>();

        for (int i = 1; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            if (linea.isBlank()) continue;
            List<String> cols = splitCsvLine(linea);
            result.add(extraerFila(i + 1, cols, colIdx));
        }
        return result;
    }

    // ── Parser Excel ───────────────────────────────────────────────────────
    private List<FilaData> parseExcel(InputStream is) throws Exception {
        List<FilaData> result = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        try (Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) return result;

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return result;

            List<String> headers = new ArrayList<>();
            for (Cell c : headerRow) headers.add(fmt.formatCellValue(c));
            Map<String, Integer> colIdx = mapearHeaders(headers);

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                List<String> cols = new ArrayList<>();
                int lastCol = colIdx.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                for (int c = 0; c <= lastCol; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    cols.add(cell != null ? fmt.formatCellValue(cell).trim() : "");
                }
                result.add(extraerFila(r + 1, cols, colIdx));
            }
        }
        return result;
    }

    // ── Procesamiento por fila ─────────────────────────────────────────────
    private ImportMiembrosResult procesarFilas(List<FilaData> filas,
                                               String tenant,
                                               String estado,
                                               UUID sedeId,
                                               UUID creadoPorId) {
        // Cargar emails existentes de una sola consulta
        Set<String> emailsExistentes = new HashSet<>(jdbc.queryForList(
                "SELECT LOWER(email) FROM " + tenant + ".miembros WHERE email IS NOT NULL AND deleted_at IS NULL",
                String.class));

        List<FilaImportError> errores   = new ArrayList<>();
        List<String>          omitidos  = new ArrayList<>();
        int importados = 0;

        for (FilaData f : filas) {
            // Validar campos obligatorios
            if (f.nombres().isBlank()) {
                errores.add(new FilaImportError(f.fila(), f.preview(), "El campo 'nombres' es obligatorio"));
                continue;
            }
            if (f.apellidos().isBlank()) {
                errores.add(new FilaImportError(f.fila(), f.preview(), "El campo 'apellidos' es obligatorio"));
                continue;
            }

            // Validar formato de email
            if (!f.email().isBlank() && !f.email().contains("@")) {
                errores.add(new FilaImportError(f.fila(), f.preview(), "Email inválido: " + f.email()));
                continue;
            }

            // Detectar duplicado por email
            if (!f.email().isBlank() && emailsExistentes.contains(f.email().toLowerCase())) {
                omitidos.add(f.email());
                log.debug("[Import] Omitido duplicado fila {}: {}", f.fila(), f.email());
                continue;
            }

            // Insertar
            try {
                UUID id    = UUID.randomUUID();
                Instant now = Instant.now();

                jdbc.update(
                        "INSERT INTO " + tenant + ".miembros "
                        + "(id, sede_id, creado_por, nombres, apellidos, email, telefono, cedula, "
                        + " estado, fecha_ingreso, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, sedeId, creadoPorId,
                        f.nombres(), f.apellidos(),
                        f.email().isBlank()   ? null : f.email(),
                        f.telefono().isBlank() ? null : f.telefono(),
                        f.cedula().isBlank()   ? null : f.cedula(),
                        estado, LocalDate.now(),
                        Timestamp.from(now), Timestamp.from(now));

                if (!f.email().isBlank()) {
                    emailsExistentes.add(f.email().toLowerCase()); // evita duplicados dentro del mismo archivo
                }
                importados++;

            } catch (Exception e) {
                log.warn("[Import] Error insertando fila {}: {}", f.fila(), e.getMessage());
                errores.add(new FilaImportError(f.fila(), f.preview(), "Error al insertar: " + e.getMessage()));
            }
        }

        log.info("[Import] tenant={} | procesados={} | importados={} | omitidos={} | errores={}",
                tenant, filas.size(), importados, omitidos.size(), errores.size());

        return new ImportMiembrosResult(
                filas.size(), importados, omitidos.size(), errores.size(),
                errores, omitidos);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private record FilaData(int fila, String nombres, String apellidos,
                             String email, String telefono, String cedula) {
        String preview() {
            return (nombres + " " + apellidos + (email.isBlank() ? "" : " <" + email + ">")).trim();
        }
    }

    private Map<String, Integer> mapearHeaders(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = normalizar(headers.get(i));
            String canonical = switch (h) {
                case "nombres", "nombre", "name", "first_name", "firstname" -> "nombres";
                case "apellidos", "apellido", "last_name", "lastname", "surname" -> "apellidos";
                case "email", "correo", "mail", "correo_electronico" -> "email";
                case "telefono", "telofono", "phone", "celular", "movil", "cel" -> "telefono";
                case "cedula", "cedula_de_ciudadania", "dni", "documento", "id", "identificacion" -> "cedula";
                default -> null;
            };
            if (canonical != null) map.put(canonical, i);
        }
        return map;
    }

    private FilaData extraerFila(int numFila, List<String> cols, Map<String, Integer> colIdx) {
        return new FilaData(
                numFila,
                valor(cols, colIdx, "nombres"),
                valor(cols, colIdx, "apellidos"),
                valor(cols, colIdx, "email"),
                valor(cols, colIdx, "telefono"),
                valor(cols, colIdx, "cedula"));
    }

    private String valor(List<String> cols, Map<String, Integer> colIdx, String campo) {
        Integer idx = colIdx.get(campo);
        if (idx == null || idx >= cols.size()) return "";
        return cols.get(idx).trim();
    }

    private String normalizar(String s) {
        return s.trim().toLowerCase()
                .replace("é", "e").replace("á", "a").replace("ó", "o")
                .replace("í", "i").replace("ú", "u").replace("ñ", "n")
                .replaceAll("[^a-z0-9_]", "_");
    }

    /** Divide una línea CSV respetando campos entre comillas dobles. */
    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if ((c == ',' || c == ';') && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result;
    }
}
