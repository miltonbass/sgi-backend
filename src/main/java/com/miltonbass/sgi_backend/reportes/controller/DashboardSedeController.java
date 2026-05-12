package com.miltonbass.sgi_backend.reportes.controller;

import com.miltonbass.sgi_backend.reportes.dto.DashboardGlobalDtos.*;
import com.miltonbass.sgi_backend.reportes.dto.DashboardSedeDtos.*;
import com.miltonbass.sgi_backend.reportes.service.DashboardGlobalService;
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
    private final DashboardGlobalService dashboardGlobalService;

    public DashboardSedeController(DashboardSedeService dashboardSedeService,
                                   DashboardGlobalService dashboardGlobalService) {
        this.dashboardSedeService = dashboardSedeService;
        this.dashboardGlobalService = dashboardGlobalService;
    }

    // ── H5.2 Dashboard Global del Pastor Principal ───────────────────────────

    @GetMapping("/dashboard-global")
    @PreAuthorize("hasAnyRole('PASTOR_PRINCIPAL','ADMIN_GLOBAL')")
    public ResponseEntity<DashboardGlobalResponse> dashboardGlobal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        LocalDate hasta = fechaHasta != null ? fechaHasta : LocalDate.now();
        LocalDate desde = fechaDesde != null ? fechaDesde : hasta.minusMonths(6);
        return ResponseEntity.ok(dashboardGlobalService.obtener(desde, hasta));
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
