package com.miltonbass.sgi_backend.auth;

import com.miltonbass.sgi_backend.auth.service.JwtService;
import com.miltonbass.sgi_backend.config.JwtProperties;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionSeguridadService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests unitarios de generación y validación de JWT (sin Spring ni base de datos). */
class JwtServiceTest {

    private static final String SECRET = "test-secret-solo-para-tests-minimo-32-caracteres";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        ConfiguracionSeguridadService seguridad = mock(ConfiguracionSeguridadService.class);
        when(seguridad.getDuracionSesionMinutos()).thenReturn(60);
        jwtService = new JwtService(props, seguridad);
    }

    @Test
    void generaTokenConTodosLosClaims() {
        String token = jwtService.generarAccessToken("user@sgi.test", "u-1", "s-1", "sede_pai_bog",
                List.of("ADMIN_SEDE", "SECRETARIA"));

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user@sgi.test");
        assertThat(claims.get("userId", String.class)).isEqualTo("u-1");
        assertThat(claims.get("sedeId", String.class)).isEqualTo("s-1");
        assertThat(claims.get("sedeSchema", String.class)).isEqualTo("sede_pai_bog");
        assertThat(jwtService.obtenerRoles(token)).containsExactly("ADMIN_SEDE", "SECRETARIA");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void cadaTokenTieneJtiUnico() {
        String t1 = jwtService.generarAccessToken("a@sgi.test", "u", null, null, List.of());
        String t2 = jwtService.generarAccessToken("a@sgi.test", "u", null, null, List.of());
        assertThat(jwtService.validarYObtenerClaims(t1).getId())
                .isNotEqualTo(jwtService.validarYObtenerClaims(t2).getId());
    }

    @Test
    void rechazaTokenConPayloadAlterado() {
        String token = jwtService.generarAccessToken("user@sgi.test", "u-1", "s-1", "sede_pai_bog",
                List.of("SECRETARIA"));
        String[] partes = token.split("[.]");
        // Intento de escalar privilegios cambiando el payload sin re-firmar
        String payloadFalso = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"user@sgi.test\",\"roles\":[\"ADMIN_GLOBAL\"]}".getBytes(StandardCharsets.UTF_8));
        String alterado = partes[0] + "." + payloadFalso + "." + partes[2];

        assertThat(jwtService.esValido(alterado)).isFalse();
        assertThatThrownBy(() -> jwtService.validarYObtenerClaims(alterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        String ajeno = Jwts.builder()
                .subject("intruso@sgi.test")
                .claim("roles", List.of("ADMIN_GLOBAL"))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("otra-clave-distinta-de-al-menos-32-caracteres!!"
                        .getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.esValido(ajeno)).isFalse();
    }

    @Test
    void rechazaTokenExpirado() {
        String expirado = Jwts.builder()
                .subject("user@sgi.test")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.esValido(expirado)).isFalse();
        assertThatThrownBy(() -> jwtService.validarYObtenerClaims(expirado))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rechazaTokenSinFirma() {
        String sinFirma = Jwts.builder().subject("user@sgi.test")
                .claim("roles", List.of("ADMIN_GLOBAL")).compact();
        assertThat(jwtService.esValido(sinFirma)).isFalse();
    }

    @Test
    void rechazaBasura() {
        assertThat(jwtService.esValido("no-es-un-jwt")).isFalse();
        assertThat(jwtService.esValido("")).isFalse();
    }
}
