package com.miltonbass.sgi_backend.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapea la tabla shared.sedes (estructura real verificada en BD).
 * Columnas usadas para auth: id, codigo, nombre, schema_name, activa.
 */
@Entity
@Table(schema = "shared", name = "sedes")
public class Sede {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    /** En BD es "nombre", no "nombre_corto" */
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

    // ─── Getters y setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    /** getNombreCorto() mantiene compatibilidad con AuthService que llama este método */
    public String getNombreCorto() { return nombre; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}