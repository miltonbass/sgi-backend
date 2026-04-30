package com.miltonbass.sgi_backend.sedes.controller;

import com.miltonbass.sgi_backend.sedes.dto.SedeDtos.*;
import com.miltonbass.sgi_backend.sedes.service.SedeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sedes")
@PreAuthorize("hasRole('ADMIN_GLOBAL')")
public class SedeController {

    private final SedeService sedeService;

    public SedeController(SedeService sedeService) {
        this.sedeService = sedeService;
    }

    @GetMapping
    public ResponseEntity<SedePageResponse> listar(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("nombre"));
        return ResponseEntity.ok(sedeService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SedeResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(sedeService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<SedeResponse> crear(
            @Valid @RequestBody CreateSedeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(sedeService.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SedeResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSedeRequest req) {
        return ResponseEntity.ok(sedeService.actualizar(id, req));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<Void> activar(@PathVariable UUID id) {
        sedeService.activar(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        sedeService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
