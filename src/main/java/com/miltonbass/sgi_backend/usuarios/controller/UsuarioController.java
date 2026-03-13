package com.miltonbass.sgi_backend.usuarios.controller;

import com.miltonbass.sgi_backend.usuarios.dto.UsuarioDtos.*;
import com.miltonbass.sgi_backend.usuarios.service.UsuarioService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // ─── POST /api/v1/usuarios ────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','SUPER_ADMIN','ADMIN_SEDE')")
    public ResponseEntity<UsuarioResponse> crear(
            @Valid @RequestBody CreateUsuarioRequest req,
            Authentication auth) {
        String rolLlamante = extraerRolPrincipal(auth);
        String sedeIdLlamante = extraerSedeId(auth);
        return ResponseEntity.status(201)
            .body(usuarioService.crearUsuario(req, rolLlamante, sedeIdLlamante));
    }

    // ─── GET /api/v1/usuarios ─────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','SUPER_ADMIN','ADMIN_SEDE')")
    public ResponseEntity<UsuarioListResponse> listar(
            @RequestParam(required = false) UUID sedeId,
            @RequestParam(required = false) String rol,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            Authentication auth) {
        String rolLlamante = extraerRolPrincipal(auth);
        String sedeIdLlamante = extraerSedeId(auth);
        return ResponseEntity.ok(
            usuarioService.listar(sedeId, rol, pagina, tamano, rolLlamante, sedeIdLlamante));
    }

    // ─── PUT /api/v1/usuarios/{id} ────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','SUPER_ADMIN','ADMIN_SEDE')")
    public ResponseEntity<UsuarioResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUsuarioRequest req,
            Authentication auth) {
        String rolLlamante = extraerRolPrincipal(auth);
        String sedeIdLlamante = extraerSedeId(auth);
        return ResponseEntity.ok(
            usuarioService.actualizar(id, req, rolLlamante, sedeIdLlamante));
    }

    // ─── POST /api/v1/usuarios/{id}/sedes ────────────────────
    @PostMapping("/{id}/sedes")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','SUPER_ADMIN','ADMIN_SEDE')")
    public ResponseEntity<UsuarioResponse> asignarSede(
            @PathVariable UUID id,
            @Valid @RequestBody AsignarSedeRequest req,
            Authentication auth) {
        String rolLlamante = extraerRolPrincipal(auth);
        String sedeIdLlamante = extraerSedeId(auth);
        return ResponseEntity.ok(
            usuarioService.asignarSede(id, req, rolLlamante, sedeIdLlamante));
    }

    // ─── DELETE /api/v1/usuarios/{id}/sedes/{sedeId} ─────────
    @DeleteMapping("/{id}/sedes/{sedeId}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','SUPER_ADMIN','ADMIN_SEDE')")
    public ResponseEntity<Void> desasignarSede(
            @PathVariable UUID id,
            @PathVariable UUID sedeId,
            Authentication auth) {
        String rolLlamante = extraerRolPrincipal(auth);
        String sedeIdLlamante = extraerSedeId(auth);
        usuarioService.desasignarSede(id, sedeId, rolLlamante, sedeIdLlamante);
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers JWT ─────────────────────────────────────────
    private String extraerRolPrincipal(Authentication auth) {
    return auth.getAuthorities().stream()
        .map(a -> a.getAuthority())
        .filter(r -> r.startsWith("ROLE_ADMIN") || r.startsWith("ROLE_SUPER"))
        .map(r -> r.replace("ROLE_", ""))   // devolver sin prefijo para comparar en el servicio
        .findFirst()
        .orElse("OTRO");
    }

    private String extraerSedeId(Authentication auth) {
        // El JwtAuthFilter pone el claim "sedeId" en los details
        if (auth.getDetails() instanceof Claims claims) {
            return claims.get("sedeId", String.class);
        }
        return null;
    }
}
