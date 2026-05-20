package com.miltonbass.sgi_backend.configuracion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

public class ConfiguracionDtos {

    // ── H7.1 — Configuración de Sede ─────────────────────────────────────────

    public record RedesSociales(
            String instagram,
            String facebook,
            String youtube
    ) {}

    public record ConfiguracionSedeResponse(
            UUID    id,
            String  codigo,
            String  nombre,
            String  descripcion,
            String  ciudad,
            String  departamento,
            String  pais,
            String  direccion,
            String  telefono,
            String  email,
            String  sitioWeb,
            String  zonaHoraria,
            LocalDate fechaFundacion,
            RedesSociales redesSociales,
            boolean activa
    ) {}

    public record ActualizarConfiguracionSedeRequest(
            @NotBlank(message = "El nombre de la sede es requerido")
            String nombre,
            String descripcion,
            String ciudad,
            String departamento,
            String pais,
            String direccion,
            String telefono,
            @Email(message = "El email de contacto no es válido")
            String email,
            String sitioWeb,
            String zonaHoraria,
            LocalDate fechaFundacion,
            RedesSociales redesSociales
    ) {}

    // ── H7.2 — Configuración SMTP ─────────────────────────────────────────────

    public record ConfiguracionSmtpResponse(
            String  host,
            Integer puerto,
            String  usuario,
            String  passwordMasked,
            String  cifrado,
            String  remitente,
            boolean activo,
            boolean configurado
    ) {}

    public record ActualizarSmtpRequest(
            @NotBlank(message = "El host SMTP es requerido")
            String host,
            @Min(value = 1,     message = "Puerto mínimo: 1")
            @Max(value = 65535, message = "Puerto máximo: 65535")
            int puerto,
            @NotBlank(message = "El usuario SMTP es requerido")
            @Email(message = "El usuario debe ser un email válido")
            String usuario,
            String password,
            @Pattern(regexp = "TLS|STARTTLS|NONE", message = "Cifrado debe ser TLS, STARTTLS o NONE")
            String cifrado,
            @NotBlank(message = "El remitente es requerido")
            String remitente,
            boolean activo
    ) {}

    public record ProbarSmtpResponse(
            boolean exitoso,
            String  mensaje
    ) {}

    // ── H7.3 — Branding / Logo ────────────────────────────────────────────────

    public record BrandingResponse(
            String  logoUrl,
            String  logoCompactoUrl,
            String  colorPrimario,
            String  colorAcento,
            boolean tieneLogoPersonalizado,
            boolean tieneLogoCompacto
    ) {}

    public record ActualizarBrandingRequest(
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colorPrimario debe ser #RRGGBB")
            String colorPrimario,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colorAcento debe ser #RRGGBB")
            String colorAcento
    ) {}

    public record SubirLogoResponse(
            String logoUrl,
            String mensaje
    ) {}

    // ── H7.4 — Notificaciones por Email ───────────────────────────────────────

    public record ConfiguracionNotificacion(
            boolean activo,
            String  cc
    ) {}

    public record NotificacionesResponse(
            ConfiguracionNotificacion nuevoMiembro,
            ConfiguracionNotificacion cambioEstado,
            ConfiguracionNotificacion alertaSeguimiento,
            ConfiguracionNotificacion nuevoUsuario
    ) {}

    public record ActualizarNotificacionesRequest(
            ConfiguracionNotificacion nuevoMiembro,
            ConfiguracionNotificacion cambioEstado,
            ConfiguracionNotificacion alertaSeguimiento,
            ConfiguracionNotificacion nuevoUsuario
    ) {}

    // ── H7.5 — Parámetros del Sistema ─────────────────────────────────────────

    public record ParametrosResponse(
            String estadoInicialMiembro,
            int    diasSinContactoAlerta,
            String moneda,
            int    periodoReportesDias,
            int    limiteMiembrosCelula
    ) {}

    public record ActualizarParametrosRequest(
            @NotBlank(message = "estadoInicialMiembro es requerido")
            @Pattern(regexp = "VISITOR|MIEMBRO",
                     message = "estadoInicialMiembro debe ser VISITOR o MIEMBRO")
            String estadoInicialMiembro,

            @Min(value = 1, message = "diasSinContactoAlerta debe ser al menos 1")
            int diasSinContactoAlerta,

            @NotBlank(message = "moneda es requerida")
            @Pattern(regexp = "COP|USD|EUR",
                     message = "moneda debe ser COP, USD o EUR")
            String moneda,

            int periodoReportesDias,

            @Min(value = 0, message = "limiteMiembrosCelula debe ser 0 o mayor")
            int limiteMiembrosCelula
    ) {}

    // ── H7.6 — Políticas de Seguridad ─────────────────────────────────────────

    public record PoliticasSeguridadResponse(
            int longitudMinimaPassword,
            int expiracionPasswordDias,
            int maxIntentosFallidos,
            int duracionSesionHoras
    ) {}

    public record ActualizarSeguridadRequest(
            @Min(value = 6,  message = "longitudMinimaPassword debe ser al menos 6")
            @Max(value = 32, message = "longitudMinimaPassword no puede superar 32")
            int longitudMinimaPassword,

            @Min(value = 0, message = "expiracionPasswordDias debe ser 0 o mayor")
            int expiracionPasswordDias,

            @Min(value = 1, message = "maxIntentosFallidos debe ser al menos 1")
            int maxIntentosFallidos,

            @Min(value = 1,   message = "duracionSesionHoras debe ser al menos 1")
            @Max(value = 168, message = "duracionSesionHoras no puede superar 168 (1 semana)")
            int duracionSesionHoras
    ) {}

    // ── H7.7 — Plantillas de Correo ───────────────────────────────────────────

    public record PlantillaCorreoResponse(
            String       tipo,
            String       asunto,
            String       cuerpo,
            java.util.List<String> variables,
            boolean      esPersonalizada
    ) {}

    public record ActualizarPlantillaRequest(
            @NotBlank(message = "asunto es requerido")
            String asunto,
            @NotBlank(message = "cuerpo es requerido")
            String cuerpo
    ) {}
}
