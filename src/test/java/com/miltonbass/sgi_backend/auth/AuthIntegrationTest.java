package com.miltonbass.sgi_backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo de autenticación contra Postgres real: login, refresh, logout y bloqueo. */
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired ObjectMapper json;

    private String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private JsonNode loginOk() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL_ADMIN, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void loginCorrectoDevuelveTokensYDatosDeSede() throws Exception {
        JsonNode body = loginOk();

        assertThat(body.path("accessToken").asText()).isNotBlank();
        assertThat(body.path("refreshToken").asText()).isNotBlank();
        assertThat(body.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.path("usuario").path("sedeSchema").asText()).isEqualTo(SCHEMA_A);
        assertThat(body.path("usuario").path("roles").toString()).contains("ADMIN_SEDE");

        // El token emitido es válido y lleva la sede correcta
        var claims = jwtService.validarYObtenerClaims(body.path("accessToken").asText());
        assertThat(claims.get("sedeSchema", String.class)).isEqualTo(SCHEMA_A);
    }

    @Test
    void passwordIncorrectoDevuelve401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL_ADMIN, "incorrecta1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void emailInexistenteDevuelveElMismoErrorQuePasswordIncorrecto() throws Exception {
        // Mismo código y mensaje: no revela si el email existe
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nadie@sgi.test", "cualquiera1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void bodyInvalidoDevuelve400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDACION_FALLIDA"));
    }

    @Test
    void refreshEmiteNuevoAccessToken() throws Exception {
        String refresh = loginOk().path("refreshToken").asText();

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refreshTokenInventadoDevuelve401() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"token-que-no-existe\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("REFRESH_TOKEN_INVALIDO"));
    }

    @Test
    void despuesDeLogoutElRefreshTokenQuedaRevocado() throws Exception {
        String refresh = loginOk().path("refreshToken").asText();
        String body = "{\"refreshToken\":\"" + refresh + "\"}";

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cuentaSeBloqueaTrasVariosIntentosFallidos() throws Exception {
        String ultimo = "";
        for (int i = 0; i < 10 && !ultimo.contains("CUENTA_BLOQUEADA"); i++) {
            ultimo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(EMAIL_BLOQUEO, "incorrecta1")))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
        }
        assertThat(ultimo).contains("CUENTA_BLOQUEADA");

        // Bloqueada: ni siquiera la contraseña correcta permite entrar
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL_BLOQUEO, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CUENTA_BLOQUEADA"));
    }

    @Test
    void sedesDelUsuarioEsPublicoYRequiereEmail() throws Exception {
        mvc.perform(get("/api/auth/sedes").param("email", EMAIL_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("PAI_BOG"));
        mvc.perform(get("/api/auth/sedes"))
                .andExpect(status().isBadRequest());
    }
}
