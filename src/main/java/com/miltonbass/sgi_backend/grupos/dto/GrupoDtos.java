package com.miltonbass.sgi_backend.grupos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class GrupoDtos {

    public record CreateGrupoRequest(
            @NotBlank String nombre,
            @NotBlank @Pattern(regexp = "CELULA|MINISTERIO|CLASE") String tipo,
            UUID liderId,
            String descripcion) {}

    public record UpdateGrupoRequest(
            String nombre,
            @Pattern(regexp = "CELULA|MINISTERIO|CLASE") String tipo,
            UUID liderId,
            String descripcion,
            Boolean activo) {}

    public record GrupoResponse(
            UUID id,
            UUID sedeId,
            String nombre,
            String tipo,
            UUID liderId,
            String liderNombre,
            String descripcion,
            boolean activo,
            int totalMiembros,
            Instant creadoEn,
            Instant actualizadoEn) {}

    public record GrupoPageResponse(
            List<GrupoResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {}

    public record AsignarMiembroRequest(
            @NotNull UUID miembroId,
            @Pattern(regexp = "LIDER|ASISTENTE|PARTICIPANTE") String rol,
            LocalDate fechaIngreso) {}

    public record MiembroGrupoResponse(
            UUID id,
            UUID miembroId,
            String nombres,
            String apellidos,
            String email,
            String telefono,
            String estado,
            String rol,
            LocalDate fechaIngreso,
            Instant creadoEn) {}

    public record GrupoMiembrosResponse(
            UUID grupoId,
            String nombre,
            String tipo,
            List<MiembroGrupoResponse> miembros) {}
}
