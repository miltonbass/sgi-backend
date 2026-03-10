package com.miltonbass.sgi_backend.auth.service;

import com.miltonbass.sgi_backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para crear y validar tokens JWT.
 * Usa JJWT 0.12.6 con firma HMAC-SHA256.
 *
 * Claims del access token:
 *   sub       → email del usuario
 *   userId    → UUID del usuario
 *   sedeId    → UUID de la sede activa (puede ser null si aún no se seleccionó)
 *   sedeSchema → schema Postgres (ej: "sede_pai_bog")
 *   roles     → lista de strings (ej: ["ADMIN", "SECRETARIA"])
 *   jti       → UUID único del token
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    // ─── Generación ──────────────────────────────────────────────────────────

    /**
     * Genera un access token JWT con todos los claims del usuario.
     *
     * @param email      email (subject)
     * @param userId     UUID del usuario
     * @param sedeId     UUID de la sede activa (puede ser null)
     * @param sedeSchema schema Postgres de la sede activa (puede ser null)
     * @param roles      lista de roles para esa sede
     * @return token JWT firmado
     */
    public String generarAccessToken(String email,
                                     String userId,
                                     String sedeId,
                                     String sedeSchema,
                                     List<String> roles) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(jwtProperties.getExpirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())          // jti — evita replay
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .claim("userId", userId)
                .claim("sedeId", sedeId)
                .claim("sedeSchema", sedeSchema)
                .claim("roles", roles)
                .signWith(llave())
                .compact();
    }

    // ─── Validación ──────────────────────────────────────────────────────────

    /**
     * Valida el token y retorna los claims si es válido.
     * Lanza JwtException si está expirado, malformado o la firma es incorrecta.
     */
    public Claims validarYObtenerClaims(String token) {
        return Jwts.parser()
                .verifyWith(llave())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Indica si el token es válido (no lanza excepción).
     */
    public boolean esValido(String token) {
        try {
            validarYObtenerClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    // ─── Extracción de claims ─────────────────────────────────────────────────

    public String obtenerEmail(String token) {
        return validarYObtenerClaims(token).getSubject();
    }

    public String obtenerUserId(String token) {
        return validarYObtenerClaims(token).get("userId", String.class);
    }

    public String obtenerSedeId(String token) {
        return validarYObtenerClaims(token).get("sedeId", String.class);
    }

    public String obtenerSedeSchema(String token) {
        return validarYObtenerClaims(token).get("sedeSchema", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> obtenerRoles(String token) {
        return (List<String>) validarYObtenerClaims(token).get("roles");
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private SecretKey llave() {
        // La clave debe tener al menos 32 bytes para HMAC-SHA256
        byte[] bytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}