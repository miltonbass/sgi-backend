package com.miltonbass.sgi_backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * En producción, impide arrancar con los secretos por defecto del repositorio.
 *
 * Los valores por defecto de application.properties son públicos (están en git):
 * con ellos cualquiera podría firmar JWT válidos o descifrar la clave SMTP.
 * Fallar al arrancar es preferible a correr en producción con esos valores.
 */
@Component
@Profile("prod")
public class ProductionSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretsValidator.class);

    static final String DEFAULT_JWT_SECRET = "dev-secret-cambiar-en-produccion-minimo-32-chars";
    static final String DEFAULT_ENCRYPTION_KEY = "dev-encryption-key-32-chars-min!";
    static final int MIN_LENGTH = 32;

    private final JwtProperties jwtProperties;
    private final String encryptionKey;

    public ProductionSecretsValidator(JwtProperties jwtProperties,
                                      @Value("${sgi.encryption.key}") String encryptionKey) {
        this.jwtProperties = jwtProperties;
        this.encryptionKey = encryptionKey;
    }

    @PostConstruct
    void validar() {
        List<String> problemas = new ArrayList<>();
        revisar("SGI_JWT_SECRET", jwtProperties.getSecret(), DEFAULT_JWT_SECRET, problemas);
        revisar("SGI_ENCRYPTION_KEY", encryptionKey, DEFAULT_ENCRYPTION_KEY, problemas);

        if (!problemas.isEmpty()) {
            String msg = "Configuración insegura en producción: " + String.join("; ", problemas);
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.info("Secretos de producción validados");
    }

    private static void revisar(String variable, String valor, String porDefecto, List<String> problemas) {
        if (valor == null || valor.isBlank()) {
            problemas.add(variable + " no está definida");
        } else if (valor.equals(porDefecto)) {
            problemas.add(variable + " usa el valor por defecto del repositorio");
        } else if (valor.length() < MIN_LENGTH) {
            problemas.add(variable + " debe tener al menos " + MIN_LENGTH + " caracteres");
        }
    }
}
