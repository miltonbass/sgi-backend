package com.miltonbass.sgi_backend.asistencias.controller;

import com.miltonbass.sgi_backend.asistencias.dto.AsistenciaDtos.*;
import com.miltonbass.sgi_backend.asistencias.service.AsistenciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/eventos/{eventoId}/asistencias")
@PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_SEDE','SECRETARIO_SEDE','REGISTRO_SEDE','LIDER_CELULA')")
public class AsistenciaController {

    private final AsistenciaService asistenciaService;

    public AsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    @GetMapping
    public ResponseEntity<CheckInListResponse> listar(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(asistenciaService.listar(eventoId));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<MiembroCheckInResponse>> buscarMiembros(
            @PathVariable UUID eventoId,
            @RequestParam String q) {
        if (q == null || q.isBlank() || q.length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(asistenciaService.buscarMiembros(eventoId, q));
    }

    @PostMapping
    public ResponseEntity<AsistenciaResponse> registrar(
            @PathVariable UUID eventoId,
            @Valid @RequestBody RegistrarAsistenciaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(asistenciaService.registrar(eventoId, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_SEDE','PASTOR_SEDE')")
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID eventoId,
            @PathVariable UUID id) {
        asistenciaService.eliminar(eventoId, id);
        return ResponseEntity.noContent().build();
    }
}
