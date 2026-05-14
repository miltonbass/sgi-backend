package com.miltonbass.sgi_backend.celulas.controller;

import com.miltonbass.sgi_backend.celulas.dto.SesionGrupoDtos.*;
import com.miltonbass.sgi_backend.celulas.service.SesionGrupoService;
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
@RequestMapping("/api/v1/grupos/{grupoId}/sesiones")
@PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE','LIDER_CELULA')")
public class SesionGrupoController {

    private final SesionGrupoService sesionService;

    public SesionGrupoController(SesionGrupoService sesionService) {
        this.sesionService = sesionService;
    }

    @GetMapping
    public ResponseEntity<List<SesionResponse>> listar(
            @PathVariable UUID grupoId, Authentication auth) {
        UUID usuarioId = usuarioIdSiLider(auth);
        return ResponseEntity.ok(sesionService.listar(grupoId, usuarioId));
    }

    @PostMapping
    public ResponseEntity<SesionResponse> crear(
            @PathVariable UUID grupoId,
            @Valid @RequestBody CreateSesionRequest req,
            Authentication auth) {
        UUID usuarioId = usuarioIdSiLider(auth);
        UUID creadoPor = extraerUsuarioId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sesionService.crear(grupoId, req, creadoPor, usuarioId));
    }

    @GetMapping("/{sesionId}")
    public ResponseEntity<SesionResponse> obtener(
            @PathVariable UUID grupoId,
            @PathVariable UUID sesionId,
            Authentication auth) {
        UUID usuarioId = usuarioIdSiLider(auth);
        return ResponseEntity.ok(sesionService.obtener(grupoId, sesionId, usuarioId));
    }

    @PutMapping("/{sesionId}")
    public ResponseEntity<SesionResponse> actualizar(
            @PathVariable UUID grupoId,
            @PathVariable UUID sesionId,
            @Valid @RequestBody UpdateSesionRequest req,
            Authentication auth) {
        UUID usuarioId = usuarioIdSiLider(auth);
        return ResponseEntity.ok(sesionService.actualizar(grupoId, sesionId, req, usuarioId));
    }

    @DeleteMapping("/{sesionId}")
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID grupoId,
            @PathVariable UUID sesionId) {
        sesionService.eliminar(grupoId, sesionId);
        return ResponseEntity.noContent().build();
    }

    // ─── Asistencia ───────────────────────────────────────────────────────────

    @GetMapping("/{sesionId}/asistencias")
    public ResponseEntity<AsistenciaSesionListResponse> listarAsistencias(
            @PathVariable UUID grupoId,
            @PathVariable UUID sesionId,
            Authentication auth) {
        UUID usuarioId = usuarioIdSiLider(auth);
        return ResponseEntity.ok(sesionService.listarAsistencias(grupoId, sesionId, usuarioId));
    }

    @PostMapping("/{sesionId}/asistencias")
    public ResponseEntity<AsistenciaSesionResponse> registrarAsistencia(
            @PathVariable UUID grupoId,
            @PathVariable UUID sesionId,
            @Valid @RequestBody RegistrarAsistenciaSesionRequest req,
            Authentication auth) {
        UUID usuarioId = usuarioIdSiLider(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sesionService.registrarAsistencia(grupoId, sesionId, req, usuarioId));
    }

    @DeleteMapping("/{sesionId}/asistencias/{asistenciaId}")
    public ResponseEntity<Void> eliminarAsistencia(
            @PathVariable UUID grupoId,
            @PathVariable UUID sesionId,
            @PathVariable UUID asistenciaId,
            Authentication auth) {
        UUID usuarioId = usuarioIdSiLider(auth);
        sesionService.eliminarAsistencia(grupoId, sesionId, asistenciaId, usuarioId);
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers JWT ──────────────────────────────────────────────────────────

    // Retorna usuarioId solo si es LIDER_CELULA; admin/pastor pasan null (sin restricción)
    private UUID usuarioIdSiLider(Authentication auth) {
        boolean esLider = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIDER_CELULA"));
        return esLider ? extraerUsuarioId(auth) : null;
    }

    private UUID extraerUsuarioId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String userId = claims.get("userId", String.class);
            if (userId != null) return UUID.fromString(userId);
        }
        throw new IllegalStateException("Token sin userId");
    }
}
