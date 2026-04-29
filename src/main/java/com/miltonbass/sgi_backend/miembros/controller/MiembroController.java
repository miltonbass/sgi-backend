// src/main/java/com/miltonbass/sgi_backend/miembros/controller/MiembroController.java
package com.miltonbass.sgi_backend.miembros.controller;

import com.miltonbass.sgi_backend.miembros.dto.MiembroDtos.*;
import com.miltonbass.sgi_backend.miembros.service.MiembroImportService;
import com.miltonbass.sgi_backend.miembros.service.MiembroService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/miembros")
public class MiembroController {

    private final MiembroService miembroService;
    private final MiembroImportService miembroImportService;

    public MiembroController(MiembroService miembroService,
                              MiembroImportService miembroImportService) {
        this.miembroService       = miembroService;
        this.miembroImportService = miembroImportService;
    }

    /**
     * GET /api/v1/miembros
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE')")
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
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE')")
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
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<MiembroResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(miembroService.obtener(id));
    }

    /**
     * POST /api/v1/miembros
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
     * PUT /api/v1/miembros/{id} — actualiza datos del perfil (NO el estado)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<MiembroResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMiembroRequest req) {
        return ResponseEntity.ok(miembroService.actualizar(id, req));
    }

    /**
     * PATCH /api/v1/miembros/{id}/estado
     * Cambia el estado espiritual del miembro con motivo obligatorio.
     */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<MiembroResponse> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody CambiarEstadoRequest req,
            Authentication auth) {
        UUID cambiadoPorId = extraerUsuarioId(auth);
        return ResponseEntity.ok(miembroService.cambiarEstado(id, req, cambiadoPorId));
    }

    /**
     * GET /api/v1/miembros/{id}/historial-estado
     */
    @GetMapping("/{id}/historial-estado")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','SECRETARIA','REGISTRO_SEDE')")
    public ResponseEntity<EstadoHistorialResponse> historialEstado(@PathVariable UUID id) {
        return ResponseEntity.ok(miembroService.obtenerHistorialEstado(id));
    }

    /**
     * PATCH /api/v1/miembros/{id}/consolidador
     * Asigna o quita consolidador. Solo válido si el miembro está en MIEMBRO o RESTAURADO.
     * consolidadorId null = quitar consolidador.
     */
    @PatchMapping("/{id}/consolidador")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<MiembroResponse> asignarConsolidador(
            @PathVariable UUID id,
            @Valid @RequestBody AsignarConsolidadorRequest req) {
        return ResponseEntity.ok(miembroService.asignarConsolidador(id, req));
    }

    /**
     * DELETE /api/v1/miembros/{id}
     * Alias de transición a INACTIVO. El motivo es obligatorio.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID id,
            @Valid @RequestBody InactivarRequest req,
            Authentication auth) {
        UUID cambiadoPorId = extraerUsuarioId(auth);
        miembroService.eliminar(id, req.motivo(), cambiadoPorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/miembros/{id}/perfil
     * Perfil completo del miembro con historial, grupos y asistencia reciente.
     * - REGISTRO_SEDE: solo datos básicos (nivelAcceso=BASICO)
     * - PASTOR_PRINCIPAL: teléfono y dirección ocultos
     * - CONSOLIDACION_SEDE: solo puede ver sus miembros asignados
     */
    @GetMapping("/{id}/perfil")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE','CONSOLIDACION_SEDE','REGISTRO_SEDE','SECRETARIA')")
    public ResponseEntity<PerfilMiembroResponse> perfil(
            @PathVariable UUID id,
            Authentication auth) {
        UUID usuarioId = extraerUsuarioId(auth);
        List<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .toList();
        return ResponseEntity.ok(miembroService.obtenerPerfil(id, usuarioId, roles));
    }

    /**
     * POST /api/v1/miembros/import
     * Importa miembros desde CSV o Excel (.xlsx/.xls).
     * Columnas aceptadas: nombres, apellidos, email, telefono, cedula (cualquier orden).
     * ?estadoDefault=VISITOR|MIEMBRO  (por defecto: VISITOR)
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<ImportMiembrosResult> importar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "estadoDefault", defaultValue = "VISITOR") String estadoDefault,
            Authentication auth) throws Exception {
        if (archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        UUID sedeId      = extraerSedeId(auth);
        UUID creadoPorId = extraerUsuarioId(auth);
        ImportMiembrosResult resultado = miembroImportService.importar(archivo, estadoDefault, sedeId, creadoPorId);
        return ResponseEntity.ok(resultado);
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
