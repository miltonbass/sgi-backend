package com.miltonbass.sgi_backend.grupos.controller;

import com.miltonbass.sgi_backend.grupos.dto.GrupoDtos.*;
import com.miltonbass.sgi_backend.grupos.service.GrupoService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grupos")
public class GrupoController {

    private final GrupoService grupoService;

    public GrupoController(GrupoService grupoService) {
        this.grupoService = grupoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE','LIDER_CELULA')")
    public ResponseEntity<GrupoPageResponse> listar(
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        UUID usuarioId = esLiderCelula(auth) ? extraerUsuarioId(auth) : null;
        return ResponseEntity.ok(grupoService.listar(activo, page, size, usuarioId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE','LIDER_CELULA')")
    public ResponseEntity<GrupoResponse> obtener(@PathVariable UUID id, Authentication auth) {
        UUID usuarioId = esLiderCelula(auth) ? extraerUsuarioId(auth) : null;
        return ResponseEntity.ok(grupoService.obtenerConAcceso(id, usuarioId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<GrupoResponse> crear(
            @Valid @RequestBody CreateGrupoRequest req,
            Authentication auth) {
        UUID sedeId = extraerSedeId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(grupoService.crear(req, sedeId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','LIDER_CELULA')")
    public ResponseEntity<GrupoResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGrupoRequest req,
            Authentication auth) {
        UUID usuarioId = esLiderCelula(auth) ? extraerUsuarioId(auth) : null;
        return ResponseEntity.ok(grupoService.actualizar(id, req, usuarioId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        grupoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/miembros")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA')")
    public ResponseEntity<MiembroGrupoResponse> asignarMiembro(
            @PathVariable UUID id,
            @Valid @RequestBody AsignarMiembroRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(grupoService.asignarMiembro(id, req));
    }

    @GetMapping("/{id}/miembros")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE','LIDER_CELULA')")
    public ResponseEntity<GrupoMiembrosResponse> listarMiembros(@PathVariable UUID id, Authentication auth) {
        UUID usuarioId = esLiderCelula(auth) ? extraerUsuarioId(auth) : null;
        return ResponseEntity.ok(grupoService.listarMiembros(id, usuarioId));
    }

    @DeleteMapping("/{id}/miembros/{miembroId}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA')")
    public ResponseEntity<Void> removerMiembro(
            @PathVariable UUID id,
            @PathVariable UUID miembroId) {
        grupoService.removerMiembro(id, miembroId);
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers JWT ──────────────────────────────────────────────────────────

    private boolean esLiderCelula(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIDER_CELULA"));
    }

    private UUID extraerUsuarioId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String userId = claims.get("userId", String.class);
            if (userId != null) return UUID.fromString(userId);
        }
        throw new IllegalStateException("Token sin userId");
    }

    private UUID extraerSedeId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String sedeId = claims.get("sedeId", String.class);
            if (sedeId != null) return UUID.fromString(sedeId);
        }
        throw new IllegalStateException("Token sin sedeId");
    }
}
