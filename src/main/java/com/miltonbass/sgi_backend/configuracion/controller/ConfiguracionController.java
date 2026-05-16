package com.miltonbass.sgi_backend.configuracion.controller;

import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionBrandingService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionNotificacionesService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSedeService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSmtpService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/configuracion")
public class ConfiguracionController {

    private final ConfiguracionSedeService             sedeService;
    private final ConfiguracionSmtpService             smtpService;
    private final ConfiguracionBrandingService         brandingService;
    private final ConfiguracionNotificacionesService   notifService;

    public ConfiguracionController(ConfiguracionSedeService sedeService,
                                   ConfiguracionSmtpService smtpService,
                                   ConfiguracionBrandingService brandingService,
                                   ConfiguracionNotificacionesService notifService) {
        this.sedeService     = sedeService;
        this.smtpService     = smtpService;
        this.brandingService = brandingService;
        this.notifService    = notifService;
    }

    // ── H7.1 — Configuración de la Sede ──────────────────────────────────────

    @GetMapping("/sede")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<ConfiguracionSedeResponse> obtenerSede(Authentication auth) {
        return ResponseEntity.ok(sedeService.obtener(sedeId(auth)));
    }

    @PutMapping("/sede")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<ConfiguracionSedeResponse> actualizarSede(
            @Valid @RequestBody ActualizarConfiguracionSedeRequest req,
            Authentication auth) {
        return ResponseEntity.ok(sedeService.actualizar(sedeId(auth), req));
    }

    // ── H7.2 — Configuración SMTP ─────────────────────────────────────────────

    @GetMapping("/smtp")
    @PreAuthorize("hasRole('ADMIN_GLOBAL')")
    public ResponseEntity<ConfiguracionSmtpResponse> obtenerSmtp() {
        return ResponseEntity.ok(smtpService.obtener());
    }

    @PutMapping("/smtp")
    @PreAuthorize("hasRole('ADMIN_GLOBAL')")
    public ResponseEntity<ConfiguracionSmtpResponse> actualizarSmtp(
            @Valid @RequestBody ActualizarSmtpRequest req,
            Authentication auth) {
        return ResponseEntity.ok(smtpService.actualizar(req, userId(auth)));
    }

    @PostMapping("/smtp/probar")
    @PreAuthorize("hasRole('ADMIN_GLOBAL')")
    public ResponseEntity<ProbarSmtpResponse> probarSmtp(Authentication auth) {
        return ResponseEntity.ok(smtpService.probar(email(auth)));
    }

    // ── H7.3 — Branding / Logo ────────────────────────────────────────────────

    @GetMapping("/branding")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE','PASTOR_PRINCIPAL','PASTOR_SEDE')")
    public ResponseEntity<BrandingResponse> obtenerBranding(Authentication auth) {
        return ResponseEntity.ok(brandingService.obtener(sedeId(auth)));
    }

    @PutMapping("/branding")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<BrandingResponse> actualizarBranding(
            @Valid @RequestBody ActualizarBrandingRequest req,
            Authentication auth) {
        return ResponseEntity.ok(brandingService.actualizarColores(sedeId(auth), req));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<SubirLogoResponse> subirLogo(
            @RequestPart(value = "logo",          required = false) MultipartFile logo,
            @RequestPart(value = "logoCompacto",  required = false) MultipartFile logoCompacto,
            Authentication auth) {
        return ResponseEntity.ok(
                brandingService.subirLogo(sedeId(auth), sedeSchema(auth), logo, logoCompacto));
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> servirLogo(Authentication auth) {
        return brandingService.servirLogo(sedeId(auth), false);
    }

    @GetMapping("/logo/compacto")
    public ResponseEntity<byte[]> servirLogoCompacto(Authentication auth) {
        return brandingService.servirLogo(sedeId(auth), true);
    }

    // ── H7.4 — Notificaciones por Email ───────────────────────────────────────

    @GetMapping("/notificaciones")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<NotificacionesResponse> obtenerNotificaciones(Authentication auth) {
        return ResponseEntity.ok(notifService.obtener(sedeId(auth)));
    }

    @PutMapping("/notificaciones")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<NotificacionesResponse> actualizarNotificaciones(
            @RequestBody ActualizarNotificacionesRequest req,
            Authentication auth) {
        return ResponseEntity.ok(notifService.actualizar(sedeId(auth), req));
    }

    // ── Helpers JWT ───────────────────────────────────────────────────────────

    private UUID sedeId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String id = claims.get("sedeId", String.class);
            if (id != null) return UUID.fromString(id);
        }
        throw new IllegalStateException("Token sin sedeId");
    }

    private String sedeSchema(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String schema = claims.get("sedeSchema", String.class);
            if (schema != null) return schema;
        }
        throw new IllegalStateException("Token sin sedeSchema");
    }

    private UUID userId(Authentication auth) {
        if (auth.getDetails() instanceof Claims claims) {
            String id = claims.get("userId", String.class);
            if (id != null) return UUID.fromString(id);
        }
        throw new IllegalStateException("Token sin userId");
    }

    private String email(Authentication auth) {
        return auth.getName();
    }
}
