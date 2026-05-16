package com.miltonbass.sgi_backend.configuracion.controller;

import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSedeService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/configuracion")
public class ConfiguracionController {

    private final ConfiguracionSedeService sedeService;

    public ConfiguracionController(ConfiguracionSedeService sedeService) {
        this.sedeService = sedeService;
    }

    // ── H7.1 — Configuración de la Sede ──────────────────────────────────────

    @GetMapping("/sede")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<ConfiguracionSedeResponse> obtenerSede(Authentication auth) {
        return ResponseEntity.ok(sedeService.obtener(sedeId(auth)));
    }

    @PutMapping("/sede")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<ConfiguracionSedeResponse> actualizarSede(
            @Valid @RequestBody ActualizarConfiguracionSedeRequest req,
            Authentication auth) {
        return ResponseEntity.ok(sedeService.actualizar(sedeId(auth), req));
    }

    // ── Helper JWT ────────────────────────────────────────────────────────────

    private UUID sedeId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String id = claims.get("sedeId", String.class);
            if (id != null) return UUID.fromString(id);
        }
        throw new IllegalStateException("Token sin sedeId");
    }
}
