package com.miltonbass.sgi_backend.consolidacion.controller;

import com.miltonbass.sgi_backend.consolidacion.dto.ConsolidacionDtos.*;
import com.miltonbass.sgi_backend.consolidacion.dto.ContactoDtos.*;
import com.miltonbass.sgi_backend.consolidacion.service.ConsolidacionService;
import com.miltonbass.sgi_backend.consolidacion.service.ContactoService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/consolidacion")
@PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE','CONSOLIDACION_SEDE')")
public class ConsolidacionController {

    private final ConsolidacionService consolidacionService;
    private final ContactoService contactoService;

    public ConsolidacionController(ConsolidacionService consolidacionService,
                                   ContactoService contactoService) {
        this.consolidacionService = consolidacionService;
        this.contactoService = contactoService;
    }

    @GetMapping("/consolidadores")
    public ResponseEntity<List<ConsolidadorResponse>> listarConsolidadores() {
        return ResponseEntity.ok(consolidacionService.listarConsolidadores());
    }

    @GetMapping("/tareas")
    public ResponseEntity<TareaConsolidacionPageResponse> listarTareas(
            @RequestParam(required = false) UUID consolidadorId,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                consolidacionService.listarTareas(consolidadorId, estado, page, size));
    }

    @PatchMapping("/tareas/{id}/completar")
    public ResponseEntity<TareaConsolidacionResponse> completarTarea(
            @PathVariable UUID id,
            @RequestBody(required = false) CompletarTareaRequest req,
            Authentication auth) {
        return ResponseEntity.ok(
                consolidacionService.completarTarea(id, extraerUserId(auth),
                        req != null ? req.notas() : null));
    }

    @GetMapping("/configuracion")
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
    public ResponseEntity<ConfiguracionConsolidacionResponse> obtenerConfiguracion() {
        return ResponseEntity.ok(consolidacionService.obtenerConfiguracion());
    }

    @PatchMapping("/configuracion")
    @PreAuthorize("hasRole('ADMIN_SEDE')")
    public ResponseEntity<ConfiguracionConsolidacionResponse> actualizarConfiguracion(
            @Valid @RequestBody ConfiguracionConsolidacionRequest req) {
        return ResponseEntity.ok(consolidacionService.actualizarConfiguracion(req));
    }

    // ── H4.2 Contactos ────────────────────────────────────────────────────────

    @PostMapping("/miembros/{miembroId}/contactos")
    @PreAuthorize("hasRole('CONSOLIDACION_SEDE')")
    public ResponseEntity<ContactoResponse> registrarContacto(
            @PathVariable UUID miembroId,
            @Valid @RequestBody RegistrarContactoRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contactoService.registrar(miembroId, extraerUserId(auth), req));
    }

    @GetMapping("/miembros/{miembroId}/contactos")
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','ADMIN_GLOBAL','PASTOR_SEDE','CONSOLIDACION_SEDE')")
    public ResponseEntity<ContactoPageResponse> listarContactos(
            @PathVariable UUID miembroId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(contactoService.listar(
                miembroId, extraerUserId(auth), extraerRoles(auth), page, size));
    }

    @GetMapping("/miembros/{miembroId}/resumen")
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','ADMIN_GLOBAL','PASTOR_SEDE','CONSOLIDACION_SEDE')")
    public ResponseEntity<ResumenContactosResponse> resumenContactos(
            @PathVariable UUID miembroId,
            Authentication auth) {
        return ResponseEntity.ok(contactoService.resumen(
                miembroId, extraerUserId(auth), extraerRoles(auth)));
    }

    private List<String> extraerRoles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .toList();
    }

    private UUID extraerUserId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String userId = claims.get("userId", String.class);
            if (userId != null) return UUID.fromString(userId);
        }
        throw new IllegalStateException("Token sin userId");
    }
}
