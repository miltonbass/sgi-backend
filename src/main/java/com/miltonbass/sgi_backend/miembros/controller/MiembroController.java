// src/main/java/com/miltonbass/sgi_backend/miembros/controller/MiembroController.java
package com.miltonbass.sgi_backend.miembros.controller;

import com.miltonbass.sgi_backend.miembros.dto.MiembroDtos.*;
import com.miltonbass.sgi_backend.miembros.service.MiembroService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/miembros")
public class MiembroController {

    private final MiembroService miembroService;

    public MiembroController(MiembroService miembroService) {
        this.miembroService = miembroService;
    }

    /**
     * GET /api/v1/miembros
     * Query params: estado, page, size
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<MiembroPageResponse> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size,
                Sort.by("apellidos").ascending().and(Sort.by("nombres").ascending()));
        return ResponseEntity.ok(miembroService.listar(estado, pageable));
    }

    /**
     * GET /api/v1/miembros/buscar?q=Garcia&estado=VISITOR
     */
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<MiembroPageResponse> buscar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size,
                Sort.by("apellidos").ascending().and(Sort.by("nombres").ascending()));
        return ResponseEntity.ok(miembroService.buscar(q, estado, pageable));
    }

    /**
     * GET /api/v1/miembros/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<MiembroResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(miembroService.obtener(id));
    }

    /**
     * POST /api/v1/miembros
     * El estado inicial VISITOR lo fuerza el servicio, no el cliente.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<MiembroResponse> crear(
            @Valid @RequestBody CreateMiembroRequest req,
            Authentication auth) {
        UUID sedeId = extraerSedeId(auth);
        UUID creadoPorId = extraerUsuarioId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(miembroService.crear(req, sedeId, creadoPorId));
    }

    /**
     * PUT /api/v1/miembros/{id}
     * Incluye posibilidad de cambiar estado (con validación de transiciones).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<MiembroResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMiembroRequest req) {
        return ResponseEntity.ok(miembroService.actualizar(id, req));
    }

    /**
     * DELETE /api/v1/miembros/{id} — soft delete
     * Restringido a admins y secretaria, no a REGISTRO_SEDE.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','SECRETARIA')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        miembroService.eliminar(id);
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