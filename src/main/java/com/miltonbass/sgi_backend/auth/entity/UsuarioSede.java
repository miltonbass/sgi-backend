package com.miltonbass.sgi_backend.auth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * Mapea la tabla shared.usuarios_sedes.
 * Contiene los roles TEXT[] de un usuario en una sede específica.
 */
@Entity
@Table(schema = "shared", name = "usuarios_sedes")
public class UsuarioSede {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "sede_id", nullable = false)
    private UUID sedeId;

    /**
     * Array de roles Postgres (TEXT[]).
     * Hibernate 6 mapea arrays nativos con @JdbcTypeCode(SqlTypes.ARRAY).
     * Ejemplo: ["ADMIN", "SECRETARIA"]
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "roles", columnDefinition = "text[]")
    private List<String> roles;

    @Column(nullable = false)
    private boolean activo = true;

    // ─── Getters y setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }

    public UUID getSedeId() { return sedeId; }
    public void setSedeId(UUID sedeId) { this.sedeId = sedeId; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}