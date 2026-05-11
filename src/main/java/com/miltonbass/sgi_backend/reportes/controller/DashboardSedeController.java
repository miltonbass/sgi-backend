package com.miltonbass.sgi_backend.reportes.controller;

import com.miltonbass.sgi_backend.reportes.dto.DashboardSedeDtos.*;
import com.miltonbass.sgi_backend.reportes.service.DashboardSedeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reportes")
@PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE','ADMIN_GLOBAL')")
public class DashboardSedeController {

    private final DashboardSedeService dashboardSedeService;

    public DashboardSedeController(DashboardSedeService dashboardSedeService) {
        this.dashboardSedeService = dashboardSedeService;
    }

    // ── H5.1 Dashboard Admin / Pastor de Sede ─────────────────────────────────

    @GetMapping("/dashboard-sede")
    public ResponseEntity<DashboardSedeResponse> dashboardSede(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        LocalDate hasta = fechaHasta != null ? fechaHasta : LocalDate.now();
        LocalDate desde = fechaDesde != null ? fechaDesde : hasta.minusMonths(6);
        return ResponseEntity.ok(dashboardSedeService.obtener(desde, hasta));
    }
}
