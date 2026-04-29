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

    /**
     * GET /api/v1/grupos?activo=true&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<GrupoPageResponse> listar(
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(grupoService.listar(activo, page, size));
    }

    /**
     * GET /api/v1/grupos/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<GrupoResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(grupoService.obtener(id));
    }

    /**
     * POST /api/v1/grupos
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<GrupoResponse> crear(
            @Valid @RequestBody CreateGrupoRequest req,
            Authentication auth) {
        UUID sedeId = extraerSedeId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(grupoService.crear(req, sedeId));
    }

    /**
     * PUT /api/v1/grupos/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<GrupoResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGrupoRequest req) {
        return ResponseEntity.ok(grupoService.actualizar(id, req));
    }

    /**
     * DELETE /api/v1/grupos/{id} — desactiva el grupo (soft delete)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        grupoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/grupos/{id}/miembros — asigna un miembro al grupo
     */
    @PostMapping("/{id}/miembros")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA')")
    public ResponseEntity<MiembroGrupoResponse> asignarMiembro(
            @PathVariable UUID id,
            @Valid @RequestBody AsignarMiembroRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(grupoService.asignarMiembro(id, req));
    }

    /**
     * GET /api/v1/grupos/{id}/miembros
     */
    @GetMapping("/{id}/miembros")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<GrupoMiembrosResponse> listarMiembros(@PathVariable UUID id) {
        return ResponseEntity.ok(grupoService.listarMiembros(id));
    }

    /**
     * DELETE /api/v1/grupos/{id}/miembros/{miembroId} — remueve miembro del grupo
     */
    @DeleteMapping("/{id}/miembros/{miembroId}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA')")
    public ResponseEntity<Void> removerMiembro(
            @PathVariable UUID id,
            @PathVariable UUID miembroId) {
        grupoService.removerMiembro(id, miembroId);
        return ResponseEntity.noContent().build();
    }

    // ─── Helper JWT ───────────────────────────────────────────────────

    private UUID extraerSedeId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String sedeId = claims.get("sedeId", String.class);
            if (sedeId != null)
                return UUID.fromString(sedeId);
        }
        throw new IllegalStateException("Token sin sedeId — acceso denegado");
    }
}
