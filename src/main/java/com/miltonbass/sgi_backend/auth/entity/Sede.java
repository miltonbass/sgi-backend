package com.miltonbass.sgi_backend.auth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
@Table(schema = "shared", name = "sedes")
public class Sede {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "schema_name", nullable = false, unique = true, length = 63)
    private String schemaName;

    @Column(length = 100)
    private String ciudad;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "created_at", updatable = false)
    private Instant creadoEn;

    @Column(name = "updated_at")
    private Instant actualizadoEn;

    @Column(length = 100)
    private String departamento;

    @Column(length = 50)
    private String pais = "Colombia";

    @Column(columnDefinition = "text")
    private String direccion;

    @Column(length = 20)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "zona_horaria", length = 50)
    private String zonaHoraria = "America/Bogota";

    @Column(name = "fecha_fundacion")
    private LocalDate fechaFundacion;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> config;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (creadoEn == null)    creadoEn = Instant.now();
        if (actualizadoEn == null) actualizadoEn = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = Instant.now();
    }


    // ─── Getters y setters ────────────────────────────────────────────

    public UUID getId()                  { return id; }
    public void setId(UUID v)            { this.id = v; }

    public String getCodigo()            { return codigo; }
    public void setCodigo(String v)      { this.codigo = v; }

    /** Compatibilidad con AuthService */
    public String getNombreCorto()       { return nombre; }
    public String getNombre()            { return nombre; }
    public void setNombre(String v)      { this.nombre = v; }

    public String getSchemaName()        { return schemaName; }
    public void setSchemaName(String v)  { this.schemaName = v; }

    public String getCiudad()            { return ciudad; }
    public void setCiudad(String v)      { this.ciudad = v; }

    public String getDescripcion()       { return descripcion; }
    public void setDescripcion(String v) { this.descripcion = v; }

    public boolean isActiva()            { return activa; }
    public void setActiva(boolean v)     { this.activa = v; }

    public Instant getCreadoEn()         { return creadoEn; }
    public Instant getActualizadoEn()    { return actualizadoEn; }
    public void setActualizadoEn(Instant v) { this.actualizadoEn = v; }

    public String getDepartamento()      { return departamento; }
    public void setDepartamento(String v){ this.departamento = v; }

    public String getPais()              { return pais; }
    public void setPais(String v)        { this.pais = v; }

    public String getDireccion()         { return direccion; }
    public void setDireccion(String v)   { this.direccion = v; }

    public String getTelefono()          { return telefono; }
    public void setTelefono(String v)    { this.telefono = v; }

    public String getEmail()             { return email; }
    public void setEmail(String v)       { this.email = v; }

    public String getLogoUrl()           { return logoUrl; }
    public void setLogoUrl(String v)     { this.logoUrl = v; }

    public String getZonaHoraria()       { return zonaHoraria; }
    public void setZonaHoraria(String v) { this.zonaHoraria = v; }

    public LocalDate getFechaFundacion()       { return fechaFundacion; }
    public void setFechaFundacion(LocalDate v) { this.fechaFundacion = v; }

    public Map<String, Object> getConfig()       { return config; }
    public void setConfig(Map<String, Object> v) { this.config = v; }

    public Instant getDeletedAt()        { return deletedAt; }
    public void setDeletedAt(Instant v)  { this.deletedAt = v; }
}
