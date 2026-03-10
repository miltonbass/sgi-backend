package com.miltonbass.sgi_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de configuración JWT leídas desde application.properties.
 * Prefijo: sgi.jwt
 */
@Component
@ConfigurationProperties(prefix = "sgi.jwt")
public class JwtProperties {

    /** Clave secreta para firmar tokens (mínimo 256 bits en producción) */
    private String secret;

    /** Duración del access token en minutos (default: 60) */
    private long expirationMinutes = 60;

    /** Duración del refresh token en días (default: 30) */
    private long refreshExpirationDays = 30;

    // Getters y setters

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }

    public long getRefreshExpirationDays() {
        return refreshExpirationDays;
    }

    public void setRefreshExpirationDays(long refreshExpirationDays) {
        this.refreshExpirationDays = refreshExpirationDays;
    }
}