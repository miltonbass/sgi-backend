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
}
