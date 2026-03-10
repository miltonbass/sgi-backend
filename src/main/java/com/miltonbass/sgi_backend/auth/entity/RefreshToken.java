package com.miltonbass.sgi_backend.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapea la tabla shared.refresh_tokens.
 * Estructura real de la tabla (verificada en BD):
 *   - activo      → equivale a "no revocado" (true = válido, false = revocado)
 *   - created_at  → timestamp de creación
 *   - dispositivo → info del cliente (IP + user-agent)
 */
@Entity
@Table(schema = "shared", name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    /**
     * Hash SHA-256 del token aleatorio.
     * Se guarda el hash, nunca el valor original.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    /**
     * true = token válido, false = revocado.
     * Usa la columna "activo" que ya existe en la tabla.
     */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "created_at", updatable = false)
    private Instant creadoEn;

    /** IP + user-agent del cliente para auditoría */
    @Column(name = "dispositivo", length = 255)
    private String dispositivo;

    @PrePersist
    protected void onCreate() {
        creadoEn = Instant.now();
    }

    // ─── Lógica de negocio ────────────────────────────────────────────────────

    public boolean estaExpirado() {
        return Instant.now().isAfter(expiraEn);
    }

    public boolean esValido() {
        return activo && !estaExpirado();
    }

    // ─── Getters y setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public Instant getCreadoEn() { return creadoEn; }

    public String getDispositivo() { return dispositivo; }
    public void setDispositivo(String dispositivo) { this.dispositivo = dispositivo; }
}