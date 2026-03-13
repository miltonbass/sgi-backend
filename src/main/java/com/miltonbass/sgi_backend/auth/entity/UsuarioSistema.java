package com.miltonbass.sgi_backend.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapea la tabla shared.usuarios_sistema (estructura real verificada en BD).
 */
@Entity
@Table(schema = "shared", name = "usuarios_sistema")
public class UsuarioSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin = false;

    @Column(name = "intentos_fallidos", nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.SMALLINT)
    private int intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "ultimo_login")
    private Instant ultimoLogin;

    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword = false;

    @Column(name = "token_reset_password", length = 255)
    private String tokenResetPassword;

    @Column(name = "token_reset_expira")
    private Instant tokenResetExpira;

    @Column(name = "created_at", updatable = false)
    private Instant creadoEn;

    @Column(name = "updated_at")
    private Instant actualizadoEn;

    // ─── Lógica de bloqueo ───────────────────────────────────────────────────

    public boolean estaBloqueado() {
        if (bloqueadoHasta == null) return false;
        return Instant.now().isBefore(bloqueadoHasta);
    }

    public void resetearIntentos() {
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
        this.ultimoLogin = Instant.now();
    }

    // ─── Getters y setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public boolean isSuperAdmin() { return superAdmin; }
    public void setSuperAdmin(boolean superAdmin) { this.superAdmin = superAdmin; }

    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }

    public Instant getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(Instant bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }

    public Instant getUltimoLogin() { return ultimoLogin; }
    public void setUltimoLogin(Instant ultimoLogin) { this.ultimoLogin = ultimoLogin; }

    public boolean isDebeCambiarPassword() { return debeCambiarPassword; }
    public void setDebeCambiarPassword(boolean debeCambiarPassword) { this.debeCambiarPassword = debeCambiarPassword; }

    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getTokenResetPassword() { return tokenResetPassword; }
    public void setTokenResetPassword(String tokenResetPassword) { this.tokenResetPassword = tokenResetPassword; }

    public Instant getTokenResetExpira() { return tokenResetExpira; }
    public void setTokenResetExpira(Instant tokenResetExpira) { this.tokenResetExpira = tokenResetExpira; }

    @PrePersist
    protected void onCreate() {
        Instant ahora = Instant.now();
        if (this.creadoEn == null)    this.creadoEn = ahora;
        if (this.actualizadoEn == null) this.actualizadoEn = ahora;
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = Instant.now();
    }
}