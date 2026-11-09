package com.miltonbass.sgi_backend.security;

import com.miltonbass.sgi_backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aislamiento multi-tenant: un usuario de una sede nunca debe ver datos de otra.
 * Cada sede vive en su propio schema Postgres; el schema sale únicamente del JWT.
 */
class TenantIsolationIntegrationTest extends IntegrationTestBase {

    private String listarMiembros(String token) throws Exception {
        return mvc.perform(get("/api/v1/miembros").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void cadaSedeSoloVeSusPropiosMiembros() throws Exception {
        String vistaA = listarMiembros(token(sedeA, SCHEMA_A, "ADMIN_SEDE"));
        String vistaB = listarMiembros(token(sedeB, SCHEMA_B, "ADMIN_SEDE"));

        assertThat(vistaA).contains("SoloSedeA").doesNotContain("SoloSedeB");
        assertThat(vistaB).contains("SoloSedeB").doesNotContain("SoloSedeA");
    }

    @Test
    void noSePuedeLeerUnMiembroDeOtraSedePorId() throws Exception {
        // Conocer el UUID de un miembro de la sede B no da acceso desde la sede A
        String body = mvc.perform(get("/api/v1/miembros/" + miembroB)
                        .header("Authorization", bearer(token(sedeA, SCHEMA_A, "ADMIN_SEDE"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SoloSedeB");
    }

    @Test
    void elHeaderXSedeSchemaEsIgnorado() throws Exception {
        // Token válido sin sede + header intentando forzar el schema de la sede A
        String body = mvc.perform(get("/api/v1/miembros")
                        .header("Authorization", bearer(token(null, null, "ADMIN_SEDE")))
                        .header("X-Sede-Schema", SCHEMA_A))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SoloSedeA");
    }

    @Test
    void unSchemaInventadoEnElTokenNoDaAccesoAOtraSede() throws Exception {
        // Aunque el token fuera firmado con un schema que no matchea el patrón, no se usa
        String body = mvc.perform(get("/api/v1/miembros")
                        .header("Authorization", bearer(token(sedeA, "public; DROP SCHEMA sede_pai_bog", "ADMIN_SEDE"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SoloSedeA");
        Integer schemas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?", Integer.class, SCHEMA_A);
        assertThat(schemas).isEqualTo(1);
    }
}
