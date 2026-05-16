package com.miltonbass.sgi_backend.configuracion.service;

import com.miltonbass.sgi_backend.auth.entity.Sede;
import com.miltonbass.sgi_backend.auth.repository.SedeRepository;
import com.miltonbass.sgi_backend.configuracion.dto.ConfiguracionDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ConfiguracionBrandingService {

    private static final Set<String> MIMES_PERMITIDOS = Set.of("image/png", "image/svg+xml");
    private static final long        MAX_BYTES        = 2L * 1024 * 1024;

    private static final String COLOR_PRIMARIO_DEFAULT = "#1976D2";
    private static final String COLOR_ACENTO_DEFAULT   = "#FF4081";

    private static final String LOGO_URL          = "/api/v1/configuracion/logo";
    private static final String LOGO_COMPACTO_URL = "/api/v1/configuracion/logo/compacto";

    private final SedeRepository sedeRepo;
    private final String         uploadPath;

    public ConfiguracionBrandingService(SedeRepository sedeRepo,
                                        @Value("${sgi.upload.path:/opt/sgi/uploads}") String uploadPath) {
        this.sedeRepo   = sedeRepo;
        this.uploadPath = uploadPath;
    }

    // ── GET /branding ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BrandingResponse obtener(UUID sedeId) {
        return toResponse(buscar(sedeId));
    }

    // ── PUT /branding ─────────────────────────────────────────────────────────

    @Transactional
    public BrandingResponse actualizarColores(UUID sedeId, ActualizarBrandingRequest req) {
        Sede sede = buscar(sedeId);

        Map<String, Object> config = sede.getConfig() != null
                ? new HashMap<>(sede.getConfig())
                : new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> branding = (Map<String, Object>)
                config.computeIfAbsent("branding", k -> new HashMap<>());

        if (req.colorPrimario() != null) branding.put("colorPrimario", req.colorPrimario());
        if (req.colorAcento()   != null) branding.put("colorAcento",   req.colorAcento());

        sede.setConfig(config);
        sede.setActualizadoEn(Instant.now());
        return toResponse(sedeRepo.save(sede));
    }

    // ── POST /logo ────────────────────────────────────────────────────────────

    @Transactional
    public SubirLogoResponse subirLogo(UUID sedeId, String sedeSchema,
                                       MultipartFile logo, MultipartFile logoCompacto) {
        if ((logo == null || logo.isEmpty()) && (logoCompacto == null || logoCompacto.isEmpty())) {
            throw new IllegalArgumentException("Debe subir al menos una imagen.");
        }

        Sede sede = buscar(sedeId);

        if (logo != null && !logo.isEmpty()) {
            validar(logo);
            String relPath = guardarArchivo(logo, sedeSchema, "logo");
            sede.setLogoUrl(relPath);
        }
        if (logoCompacto != null && !logoCompacto.isEmpty()) {
            validar(logoCompacto);
            String relPath = guardarArchivo(logoCompacto, sedeSchema, "logo-compacto");
            sede.setLogoCompactoUrl(relPath);
        }

        sede.setActualizadoEn(Instant.now());
        sedeRepo.save(sede);
        return new SubirLogoResponse(LOGO_URL, "Logo actualizado correctamente");
    }

    // ── GET /logo y /logo/compacto ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> servirLogo(UUID sedeId, boolean compacto) {
        Sede sede = buscar(sedeId);
        String relPath = compacto ? sede.getLogoCompactoUrl() : sede.getLogoUrl();

        if (relPath == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Path.of(uploadPath).resolve(relPath);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String mime = relPath.endsWith(".svg") ? "image/svg+xml" : "image/png";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime))
                    .body(bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error leyendo logo: " + e.getMessage());
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private Sede buscar(UUID sedeId) {
        return sedeRepo.findById(sedeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada"));
    }

    private void validar(MultipartFile file) {
        String mime = file.getContentType();
        if (mime == null || !MIMES_PERMITIDOS.contains(mime)) {
            throw new IllegalArgumentException("Formato no permitido. Solo se aceptan PNG y SVG.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo supera el máximo de 2 MB.");
        }
    }

    private String guardarArchivo(MultipartFile file, String sedeSchema, String nombreBase) {
        String ext = "image/svg+xml".equals(file.getContentType()) ? "svg" : "png";
        String nombre = nombreBase + "." + ext;
        Path dir = Path.of(uploadPath, sedeSchema);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(nombre), file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error guardando logo: " + e.getMessage());
        }
        return sedeSchema + "/" + nombre;
    }

    @SuppressWarnings("unchecked")
    private BrandingResponse toResponse(Sede s) {
        Map<String, Object> config   = s.getConfig() != null ? s.getConfig() : Map.of();
        Object brandingObj = config.get("branding");
        Map<String, Object> branding = brandingObj instanceof Map<?,?> m
                ? (Map<String, Object>) m
                : Map.of();

        String colorPrimario = (String) branding.getOrDefault("colorPrimario", COLOR_PRIMARIO_DEFAULT);
        String colorAcento   = (String) branding.getOrDefault("colorAcento",   COLOR_ACENTO_DEFAULT);

        boolean tieneLogo     = s.getLogoUrl()          != null;
        boolean tieneCompacto = s.getLogoCompactoUrl()  != null;

        return new BrandingResponse(
                LOGO_URL,
                LOGO_COMPACTO_URL,
                colorPrimario,
                colorAcento,
                tieneLogo,
                tieneCompacto);
    }
}
