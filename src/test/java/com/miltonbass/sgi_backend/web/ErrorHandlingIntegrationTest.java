package com.miltonbass.sgi_backend.web;

import com.miltonbass.sgi_backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los errores del cliente deben responder 4xx con formato uniforme, nunca 500. */
class ErrorHandlingIntegrationTest extends IntegrationTestBase {

    @Test
    void healthEsPublicoYEstaUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void jsonMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CUERPO_INVALIDO"));
    }

    @Test
    void contentTypeNoSoportadoDevuelve415() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rutaInexistenteConTokenDevuelve404() throws Exception {
        mvc.perform(get("/api/v1/no-existe")
                        .header("Authorization", bearer(token(sedeA, SCHEMA_A, "ADMIN_SEDE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void metodoNoPermitidoDevuelve405() throws Exception {
        mvc.perform(delete("/api/auth/sedes")
                        .header("Authorization", bearer(token(sedeA, SCHEMA_A, "ADMIN_SEDE"))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void uuidInvalidoEnRutaDevuelve400() throws Exception {
        mvc.perform(get("/api/v1/miembros/no-es-uuid")
                        .header("Authorization", bearer(token(sedeA, SCHEMA_A, "ADMIN_SEDE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
    }
}
