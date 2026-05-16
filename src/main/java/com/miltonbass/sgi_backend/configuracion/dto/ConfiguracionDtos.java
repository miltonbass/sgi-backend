package com.miltonbass.sgi_backend.configuracion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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
}
