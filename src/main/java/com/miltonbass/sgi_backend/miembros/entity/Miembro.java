// src/main/java/com/miltonbass/sgi_backend/miembros/entity/Miembro.java
package com.miltonbass.sgi_backend.miembros.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "miembros")
public class Miembro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sede_id", nullable = false)
    private UUID sedeId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "creado_por")
    private UUID creadoPor;

    @Column(name = "numero_miembro", length = 20, unique = true)
    private String numeroMiembro;

    @Column(length = 20)
    private String cedula;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(length = 10)
    private String genero;

    @Column(name = "estado_civil", length = 20)
    private String estadoCivil;

    @Column(length = 20)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(columnDefinition = "text")
    private String direccion;

    @Column(length = 100)
    private String ciudad;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(nullable = false, length = 20)
    private String estado = "VISITOR";

    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column(name = "fecha_bautismo")
    private LocalDate fechaBautismo;

    @Column(name = "grupo_id")
    private UUID grupoId;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "created_at", updatable = false)
    private Instant creadoEn;

    @Column(name = "updated_at")
    private Instant actualizadoEn;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        Instant ahora = Instant.now();
        if (creadoEn == null)
            creadoEn = ahora;
        if (actualizadoEn == null)
            actualizadoEn = ahora;
        if (estado == null)
            estado = "VISITOR";
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = Instant.now();
    }

    // ─── Getters y setters ────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSedeId() {
        return sedeId;
    }

    public void setSedeId(UUID sedeId) {
        this.sedeId = sedeId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public UUID getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(UUID creadoPor) {
        this.creadoPor = creadoPor;
    }

    public String getNumeroMiembro() {
        return numeroMiembro;
    }

    public void setNumeroMiembro(String v) {
        this.numeroMiembro = v;
    }

    public String getCedula() {
        return cedula;
    }

    public void setCedula(String v) {
        this.cedula = v;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String v) {
        this.nombres = v;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String v) {
        this.apellidos = v;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate v) {
        this.fechaNacimiento = v;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(String v) {
        this.genero = v;
    }

    public String getEstadoCivil() {
        return estadoCivil;
    }

    public void setEstadoCivil(String v) {
        this.estadoCivil = v;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String v) {
        this.telefono = v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        this.email = v;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String v) {
        this.direccion = v;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String v) {
        this.ciudad = v;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String v) {
        this.fotoUrl = v;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String v) {
        this.estado = v;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate v) {
        this.fechaIngreso = v;
    }

    public LocalDate getFechaBautismo() {
        return fechaBautismo;
    }

    public void setFechaBautismo(LocalDate v) {
        this.fechaBautismo = v;
    }

    public UUID getGrupoId() {
        return grupoId;
    }

    public void setGrupoId(UUID v) {
        this.grupoId = v;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> v) {
        this.metadata = v;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(Instant v) {
        this.actualizadoEn = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant v) {
        this.deletedAt = v;
    }
}