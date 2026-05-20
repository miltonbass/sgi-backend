package com.miltonbass.sgi_backend.configuracion.controller;

import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import com.miltonbass.sgi_backend.configuracion.service.AuditoriaConfiguracionService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionBrandingService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionDominioService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionNotificacionesService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionParametrosService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionPlantillasService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSedeService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSeguridadService;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSmtpService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/configuracion")
public class ConfiguracionController {

    private final ConfiguracionSedeService           sedeService;
    private final ConfiguracionSmtpService           smtpService;
    private final ConfiguracionBrandingService       brandingService;
    private final ConfiguracionNotificacionesService notifService;
    private final ConfiguracionParametrosService     parametrosService;
    private final ConfiguracionSeguridadService      seguridadService;
    private final ConfiguracionPlantillasService     plantillasService;
    private final AuditoriaConfiguracionService      auditoriaService;
    private final ConfiguracionDominioService        dominioService;

    public ConfiguracionController(ConfiguracionSedeService sedeService,
                                   ConfiguracionSmtpService smtpService,
                                   ConfiguracionBrandingService brandingService,
                                   ConfiguracionNotificacionesService notifService,
                                   ConfiguracionParametrosService parametrosService,
                                   ConfiguracionSeguridadService seguridadService,
                                   ConfiguracionPlantillasService plantillasService,
                                   AuditoriaConfiguracionService auditoriaService,
                                   ConfiguracionDominioService dominioService) {
        this.sedeService       = sedeService;
        this.smtpService       = smtpService;
        this.brandingService   = brandingService;
        this.notifService      = notifService;
        this.parametrosService = parametrosService;
        this.seguridadService  = seguridadService;
        this.plantillasService = plantillasService;
        this.auditoriaService  = auditoriaService;
        this.dominioService    = dominioService;
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
        var result = sedeService.actualizar(sedeId(auth), req);
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "sede", "ACTUALIZAR", detalleSede(req));
        return ResponseEntity.ok(result);
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
        var result = smtpService.actualizar(req, userId(auth));
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "smtp", "ACTUALIZAR", detalleSmtp(req));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/smtp/probar")
    @PreAuthorize("hasRole('ADMIN_GLOBAL')")
    public ResponseEntity<ProbarSmtpResponse> probarSmtp(Authentication auth) {
        var result = smtpService.probar(email(auth));
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "smtp", "PROBAR_SMTP",
                Map.of("exitoso", result.exitoso(), "mensaje", result.mensaje()));
        return ResponseEntity.ok(result);
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
        var result = brandingService.actualizarColores(sedeId(auth), req);
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "branding", "ACTUALIZAR", detalleBranding(req));
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<SubirLogoResponse> subirLogo(
            @RequestPart(value = "logo",         required = false) MultipartFile logo,
            @RequestPart(value = "logoCompacto", required = false) MultipartFile logoCompacto,
            Authentication auth) {
        var result = brandingService.subirLogo(sedeId(auth), sedeSchema(auth), logo, logoCompacto);
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "branding", "SUBIR_LOGO", detalleLogo(logo, logoCompacto));
        return ResponseEntity.ok(result);
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
        var result = notifService.actualizar(sedeId(auth), req);
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "notificaciones", "ACTUALIZAR", detalleNotificaciones(req));
        return ResponseEntity.ok(result);
    }

    // ── H7.5 — Parámetros del Sistema ────────────────────────────────────────

    @GetMapping("/parametros")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<ParametrosResponse> obtenerParametros(Authentication auth) {
        return ResponseEntity.ok(parametrosService.obtener(sedeId(auth)));
    }

    @PutMapping("/parametros")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<ParametrosResponse> actualizarParametros(
            @Valid @RequestBody ActualizarParametrosRequest req,
            Authentication auth) {
        var result = parametrosService.actualizar(sedeId(auth), req);
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "parametros", "ACTUALIZAR", detalleParametros(req));
        return ResponseEntity.ok(result);
    }

    // ── H7.6 — Políticas de Seguridad ────────────────────────────────────────

    @GetMapping("/seguridad")
    @PreAuthorize("hasRole('ADMIN_GLOBAL')")
    public ResponseEntity<PoliticasSeguridadResponse> obtenerSeguridad() {
        return ResponseEntity.ok(seguridadService.obtener());
    }

    @PutMapping("/seguridad")
    @PreAuthorize("hasRole('ADMIN_GLOBAL')")
    public ResponseEntity<PoliticasSeguridadResponse> actualizarSeguridad(
            @Valid @RequestBody ActualizarSeguridadRequest req,
            Authentication auth) {
        var result = seguridadService.actualizar(req, userId(auth));
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "seguridad", "ACTUALIZAR", detalleSeguridad(req));
        return ResponseEntity.ok(result);
    }

    // ── H7.7 — Plantillas de Correo ──────────────────────────────────────────

    @GetMapping("/plantillas/{tipo}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<PlantillaCorreoResponse> obtenerPlantilla(
            @PathVariable String tipo,
            Authentication auth) {
        return ResponseEntity.ok(plantillasService.obtener(sedeId(auth), tipo));
    }

    @PutMapping("/plantillas/{tipo}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<PlantillaCorreoResponse> actualizarPlantilla(
            @PathVariable String tipo,
            @Valid @RequestBody ActualizarPlantillaRequest req,
            Authentication auth) {
        var result = plantillasService.actualizar(sedeId(auth), tipo, req);
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "plantillas", "ACTUALIZAR",
                Map.of("tipo", tipo, "asunto", req.asunto(), "cuerpo", req.cuerpo()));
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/plantillas/{tipo}")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<PlantillaCorreoResponse> restaurarPlantilla(
            @PathVariable String tipo,
            Authentication auth) {
        var result = plantillasService.restaurar(sedeId(auth), tipo);
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "plantillas", "RESTAURAR_DEFAULT", Map.of("tipo", tipo));
        return ResponseEntity.ok(result);
    }

    // ── H7.9 — Configuración de Dominio ──────────────────────────────────────

    @GetMapping("/dominio")
    @PreAuthorize("hasRole('ADMIN_GLOBAL')")
    public ResponseEntity<ConfiguracionDominioResponse> obtenerDominio() {
        return ResponseEntity.ok(dominioService.obtener());
    }

    @PutMapping("/dominio")
    @PreAuthorize("hasRole('ADMIN_GLOBAL')")
    public ResponseEntity<ConfiguracionDominioResponse> actualizarDominio(
            @Valid @RequestBody ActualizarDominioRequest req,
            Authentication auth) {
        var result = dominioService.actualizar(req, userId(auth));
        auditoriaService.registrar(sedeId(auth), userId(auth), email(auth),
                "dominio", "ACTUALIZAR", detalleDominio(req));
        return ResponseEntity.ok(result);
    }

    // ── H7.8 — Auditoría de Configuración ────────────────────────────────────

    @GetMapping("/auditoria")
    @PreAuthorize("hasAnyRole('ADMIN_GLOBAL','ADMIN_SEDE')")
    public ResponseEntity<AuditoriaPageResponse> obtenerAuditoria(
            @RequestParam(required = false) String seccion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "0")  int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            Authentication auth) {
        int tam = Math.min(Math.max(tamano, 1), 100);
        return ResponseEntity.ok(
                auditoriaService.listar(sedeId(auth), seccion, fechaDesde, fechaHasta, pagina, tam));
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

    // ── Helpers de detalle para auditoría ────────────────────────────────────

    private Map<String, Object> detalleSede(ActualizarConfiguracionSedeRequest req) {
        var m = new LinkedHashMap<String, Object>();
        if (req.nombre()       != null) m.put("nombre",       req.nombre());
        if (req.descripcion()  != null) m.put("descripcion",  req.descripcion());
        if (req.ciudad()       != null) m.put("ciudad",       req.ciudad());
        if (req.departamento() != null) m.put("departamento", req.departamento());
        if (req.pais()         != null) m.put("pais",         req.pais());
        if (req.direccion()    != null) m.put("direccion",    req.direccion());
        if (req.telefono()     != null) m.put("telefono",     req.telefono());
        if (req.email()        != null) m.put("email",        req.email());
        if (req.sitioWeb()     != null) m.put("sitioWeb",     req.sitioWeb());
        if (req.zonaHoraria()  != null) m.put("zonaHoraria",  req.zonaHoraria());
        return m;
    }

    private Map<String, Object> detalleSmtp(ActualizarSmtpRequest req) {
        var m = new LinkedHashMap<String, Object>();
        m.put("host",      req.host());
        m.put("puerto",    req.puerto());
        m.put("usuario",   req.usuario());
        if (req.cifrado()  != null) m.put("cifrado",   req.cifrado());
        m.put("remitente", req.remitente());
        m.put("activo",    req.activo());
        if (req.password() != null && !req.password().isBlank()) m.put("password", "••••••••");
        return m;
    }

    private Map<String, Object> detalleBranding(ActualizarBrandingRequest req) {
        var m = new LinkedHashMap<String, Object>();
        if (req.colorPrimario() != null) m.put("colorPrimario", req.colorPrimario());
        if (req.colorAcento()   != null) m.put("colorAcento",   req.colorAcento());
        return m;
    }

    private Map<String, Object> detalleLogo(MultipartFile logo, MultipartFile logoCompacto) {
        var m = new LinkedHashMap<String, Object>();
        if (logo         != null && !logo.isEmpty())         m.put("logo",         "actualizado");
        if (logoCompacto != null && !logoCompacto.isEmpty()) m.put("logoCompacto", "actualizado");
        return m;
    }

    private Map<String, Object> detalleNotificaciones(ActualizarNotificacionesRequest req) {
        var m = new LinkedHashMap<String, Object>();
        m.put("nuevoMiembro",      cfgToMap(req.nuevoMiembro()));
        m.put("cambioEstado",      cfgToMap(req.cambioEstado()));
        m.put("alertaSeguimiento", cfgToMap(req.alertaSeguimiento()));
        m.put("nuevoUsuario",      cfgToMap(req.nuevoUsuario()));
        return m;
    }

    private Map<String, Object> cfgToMap(ConfiguracionNotificacion cfg) {
        if (cfg == null) return Map.of("activo", false, "cc", "");
        return Map.of("activo", cfg.activo(), "cc", cfg.cc() != null ? cfg.cc() : "");
    }

    private Map<String, Object> detalleParametros(ActualizarParametrosRequest req) {
        return Map.of(
                "estadoInicialMiembro",  req.estadoInicialMiembro(),
                "diasSinContactoAlerta", req.diasSinContactoAlerta(),
                "moneda",                req.moneda(),
                "periodoReportesDias",   req.periodoReportesDias(),
                "limiteMiembrosCelula",  req.limiteMiembrosCelula());
    }

    private Map<String, Object> detalleSeguridad(ActualizarSeguridadRequest req) {
        return Map.of(
                "longitudMinimaPassword",  req.longitudMinimaPassword(),
                "expiracionPasswordDias",  req.expiracionPasswordDias(),
                "maxIntentosFallidos",     req.maxIntentosFallidos(),
                "duracionSesionHoras",     req.duracionSesionHoras());
    }

    private Map<String, Object> detalleDominio(ActualizarDominioRequest req) {
        var m = new LinkedHashMap<String, Object>();
        m.put("dominioApp", req.dominioApp());
        if (req.dominioApi()   != null) m.put("dominioApi",   req.dominioApi());
        if (req.corsOrigenes() != null) m.put("corsOrigenes", req.corsOrigenes());
        return m;
    }
}
