package com.miltonbass.sgi_backend.security;

import com.miltonbass.sgi_backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresión de las inyecciones SQL corregidas en el filtro "estado"
 * (AlertaService.listar y ConsolidacionService.listarTareas).
 * El fixture tiene 1 alerta y 1 tarea PENDIENTE en la sede A.
 */
class SqlInjectionRegressionTest extends IntegrationTestBase {

    private static final String PAYLOAD_OR = "x' OR '1'='1";

    private String admin() {
        return bearer(token(sedeA, SCHEMA_A, "ADMIN_SEDE"));
    }

    @Test
    void alertasFiltroValidoFunciona() throws Exception {
        mvc.perform(get("/api/v1/alertas").param("estado", "pendiente").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void alertasInyeccionNoDevuelveDatos() throws Exception {
        mvc.perform(get("/api/v1/alertas").param("estado", PAYLOAD_OR).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void tareasFiltroValidoFunciona() throws Exception {
        mvc.perform(get("/api/v1/consolidacion/tareas").param("estado", "PENDIENTE").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void tareasInyeccionNoDevuelveDatos() throws Exception {
        mvc.perform(get("/api/v1/consolidacion/tareas").param("estado", PAYLOAD_OR).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void consolidadorSinMiembroVinculadoNoVeTareasAjenas() throws Exception {
        // adminUserId no está vinculado a ningún miembro: antes veía TODAS las tareas
        mvc.perform(get("/api/v1/consolidacion/tareas")
                        .header("Authorization", bearer(token(sedeA, SCHEMA_A, "CONSOLIDACION_SEDE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
