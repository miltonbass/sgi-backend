package com.miltonbass.sgi_backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsValidatorTest {

    private static final String JWT_OK = "un-secreto-jwt-real-y-largo-de-mas-de-32-chars";
    private static final String KEY_OK = "una-clave-de-cifrado-real-de-mas-de-32-chars";

    private ProductionSecretsValidator validator(String jwt, String key) {
        JwtProperties props = new JwtProperties();
        props.setSecret(jwt);
        return new ProductionSecretsValidator(props, key);
    }

    @Test
    void aceptaSecretosPropios() {
        assertThatCode(() -> validator(JWT_OK, KEY_OK).validar()).doesNotThrowAnyException();
    }

    @Test
    void rechazaJwtPorDefecto() {
        assertThatThrownBy(() -> validator(ProductionSecretsValidator.DEFAULT_JWT_SECRET, KEY_OK).validar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SGI_JWT_SECRET");
    }

    @Test
    void rechazaClaveDeCifradoPorDefecto() {
        assertThatThrownBy(() -> validator(JWT_OK, ProductionSecretsValidator.DEFAULT_ENCRYPTION_KEY).validar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SGI_ENCRYPTION_KEY");
    }

    @Test
    void rechazaSecretoVacioOCorto() {
        assertThatThrownBy(() -> validator("", KEY_OK).validar()).hasMessageContaining("no está definida");
        assertThatThrownBy(() -> validator("corto", KEY_OK).validar()).hasMessageContaining("al menos 32");
    }
}
