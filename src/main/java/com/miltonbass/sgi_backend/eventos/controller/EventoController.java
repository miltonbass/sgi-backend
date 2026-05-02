package com.miltonbass.sgi_backend.eventos.controller;

import com.miltonbass.sgi_backend.eventos.dto.EventoDtos.*;
import com.miltonbass.sgi_backend.eventos.service.EventoService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/eventos")
@PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE','LIDER_GRUPO','SECRETARIO_SEDE')")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    @GetMapping
    public ResponseEntity<EventoPageResponse> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventoService.listar(tipo, estado, desde, hasta, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(eventoService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
    public ResponseEntity<EventoResponse> crear(
            @Valid @RequestBody CreateEventoRequest req,
            Authentication auth) {
        UUID sedeId    = extraerSedeId(auth);
        UUID creadoPor = extraerUsuarioId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventoService.crear(req, sedeId, creadoPor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
    public ResponseEntity<EventoResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventoRequest req) {
        return ResponseEntity.ok(eventoService.actualizar(id, req));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
    public ResponseEntity<EventoResponse> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody CambiarEstadoEventoRequest req) {
        return ResponseEntity.ok(eventoService.cambiarEstado(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        eventoService.cancelar(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers JWT ──────────────────────────────────────────────────

    private UUID extraerSedeId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String sedeId = claims.get("sedeId", String.class);
            if (sedeId != null)
                return UUID.fromString(sedeId);
        }
        throw new IllegalStateException("Token sin sedeId — acceso denegado");
    }

    private UUID extraerUsuarioId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String userId = claims.get("userId", String.class);
            if (userId != null)
                return UUID.fromString(userId);
        }
        throw new IllegalStateException("Token sin userId — acceso denegado");
    }
}
