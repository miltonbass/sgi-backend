package com.miltonbass.sgi_backend.alertas.controller;

import com.miltonbass.sgi_backend.alertas.dto.AlertaDtos.*;
import com.miltonbass.sgi_backend.alertas.service.AlertaService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alertas")
@PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @PostMapping("/detectar")
    public ResponseEntity<DeteccionResult> detectar() {
        return ResponseEntity.ok(alertaService.detectar());
    }

    @GetMapping
    public ResponseEntity<AlertaPageResponse> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertaService.listar(estado, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertaResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(alertaService.obtener(id));
    }

    @PatchMapping("/{id}/gestionar")
    public ResponseEntity<AlertaResponse> gestionar(
            @PathVariable UUID id,
            @RequestBody(required = false) GestionarAlertaRequest req,
            Authentication auth) {
        return ResponseEntity.ok(alertaService.gestionar(id, req, extraerUserId(auth)));
    }

    @PatchMapping("/{id}/descartar")
    public ResponseEntity<AlertaResponse> descartar(
            @PathVariable UUID id,
            @RequestBody(required = false) GestionarAlertaRequest req,
            Authentication auth) {
        return ResponseEntity.ok(alertaService.descartar(id, req, extraerUserId(auth)));
    }

    @GetMapping("/configuracion")
    public ResponseEntity<ConfiguracionAlertaResponse> obtenerConfiguracion() {
        return ResponseEntity.ok(alertaService.obtenerConfiguracion());
    }

    @PatchMapping("/configuracion")
    @PreAuthorize("hasRole('ADMIN_SEDE')")
    public ResponseEntity<ConfiguracionAlertaResponse> actualizarConfiguracion(
            @Valid @RequestBody ConfiguracionAlertaRequest req) {
        return ResponseEntity.ok(alertaService.actualizarConfiguracion(req));
    }

    private UUID extraerUserId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String userId = claims.get("userId", String.class);
            if (userId != null) return UUID.fromString(userId);
        }
        throw new IllegalStateException("Token sin userId");
    }
}
