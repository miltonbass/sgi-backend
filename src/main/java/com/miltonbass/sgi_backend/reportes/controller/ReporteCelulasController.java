package com.miltonbass.sgi_backend.reportes.controller;

import com.miltonbass.sgi_backend.reportes.dto.ReporteCelulasDtos.*;
import com.miltonbass.sgi_backend.reportes.service.ReporteCelulasService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reportes/celulas")
@PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
public class ReporteCelulasController {

    private final ReporteCelulasService service;

    public ReporteCelulasController(ReporteCelulasService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/reportes/celulas
     * Vista árbol completa con métricas por período.
     *
     * @param fechaDesde  inicio del período (default: hace 30 días)
     * @param fechaHasta  fin del período   (default: hoy)
     * @param nivel       0=raíz, 1=hijas, 2=nietas — omitir para todas
     * @param soloActivas true (default) muestra solo células activas
     */
    @GetMapping
    public ResponseEntity<ReporteCelulasResponse> arbol(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) Integer nivel,
            @RequestParam(defaultValue = "true") boolean soloActivas) {

        LocalDate hasta  = fechaHasta != null ? fechaHasta : LocalDate.now();
        LocalDate desde  = fechaDesde != null ? fechaDesde : hasta.minusDays(30);
        return ResponseEntity.ok(service.obtenerArbol(desde, hasta, nivel, soloActivas));
    }

    /**
     * GET /api/v1/reportes/celulas/{id}
     * Detalle de una célula: sesiones + lista de miembros con % asistencia.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DetalleCelulaResponse> detalle(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {

        LocalDate hasta  = fechaHasta != null ? fechaHasta : LocalDate.now();
        LocalDate desde  = fechaDesde != null ? fechaDesde : hasta.minusDays(30);
        return ResponseEntity.ok(service.obtenerDetalle(id, desde, hasta));
    }
}
