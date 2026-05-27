package com.miltonbass.sgi_backend.auditoria.controller;

import com.miltonbass.sgi_backend.auditoria.dto.AuditoriaDtos.*;
import com.miltonbass.sgi_backend.auditoria.service.AuditoriaGeneralService;
import io.jsonwebtoken.Claims;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
public class AuditoriaGeneralController {

    private final AuditoriaGeneralService auditoriaService;

    public AuditoriaGeneralController(AuditoriaGeneralService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public ResponseEntity<AuditoriaPageResponse> listar(
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String usuarioEmail,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {

        boolean esGlobal = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN_GLOBAL"));

        UUID sedeId = null;
        if (auth.getDetails() instanceof Claims claims) {
            String sid = claims.get("sedeId", String.class);
            if (sid != null) sedeId = UUID.fromString(sid);
        }

        return ResponseEntity.ok(auditoriaService.listar(
                sedeId, esGlobal,
                modulo, accion, usuarioEmail, resultado,
                fechaDesde, fechaHasta,
                page, size
        ));
    }
}
