package com.miltonbass.sgi_backend.miembros;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas de negocio del módulo de miembros: alta, edición, estados, baja y búsqueda. */
class MiembroIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/miembros";

    @Autowired ObjectMapper json;

    private String secretaria() { return bearer(token(sedeA, SCHEMA_A, "SECRETARIA")); }
    private String pastor()     { return bearer(token(sedeA, SCHEMA_A, "PASTOR_SEDE")); }
    private String admin()      { return bearer(token(sedeA, SCHEMA_A, "ADMIN_SEDE")); }

    /** Crea un miembro único (email/apellido aleatorios) y devuelve su id. */
    private UUID crearMiembro(String apellido) throws Exception {
        String email = "m" + UUID.randomUUID().toString().substring(0, 8) + "@sgi.test";
        String body = "{\"nombres\":\"Carlos\",\"apellidos\":\"" + apellido + "\",\"email\":\"" + email
                + "\",\"telefono\":\"3001234567\"}";
        String resp = mvc.perform(post(BASE).header("Authorization", secretaria())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(resp).path("id").asText());
    }

    private String cambioEstado(String estado) {
        return "{\"estado\":\"" + estado + "\",\"motivo\":\"test\"}";
    }

    // ── Alta ─────────────────────────────────────────────────────────────────

    @Test
    void altaCreaVisitorEnLaSedeDelToken() throws Exception {
        String resp = mvc.perform(post(BASE).header("Authorization", secretaria())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\"Lucia\",\"apellidos\":\"Nueva\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("VISITOR"))
                .andExpect(jsonPath("$.sedeId").value(sedeA.toString()))
                .andExpect(jsonPath("$.fechaIngreso").value(LocalDate.now().toString()))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(json.readTree(resp).path("id").asText());
        Integer enA = jdbc.queryForObject("SELECT COUNT(*) FROM " + SCHEMA_A + ".miembros WHERE id = ?", Integer.class, id);
        Integer enB = jdbc.queryForObject("SELECT COUNT(*) FROM " + SCHEMA_B + ".miembros WHERE id = ?", Integer.class, id);
        assertThat(enA).isEqualTo(1);
        assertThat(enB).isZero();
    }

    @Test
    void altaRechazaEmailDuplicado() throws Exception {
        String body = "{\"nombres\":\"Pedro\",\"apellidos\":\"Duplicado\",\"email\":\"dup@sgi.test\"}";
        mvc.perform(post(BASE).header("Authorization", secretaria())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post(BASE).header("Authorization", secretaria())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void altaValidaCamposObligatoriosYFormato() throws Exception {
        mvc.perform(post(BASE).header("Authorization", secretaria())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\"\",\"apellidos\":\"X\",\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDACION_FALLIDA"));
    }

    @Test
    void pastorNoPuedeDarDeAltaMiembros() throws Exception {
        mvc.perform(post(BASE).header("Authorization", pastor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\"Lucia\",\"apellidos\":\"Nueva\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Edición ──────────────────────────────────────────────────────────────

    @Test
    void edicionParcialSoloCambiaLosCamposEnviados() throws Exception {
        UUID id = crearMiembro("Parcial");

        mvc.perform(put(BASE + "/" + id).header("Authorization", secretaria())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ciudad\":\"Bogotá\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudad").value("Bogotá"))
                .andExpect(jsonPath("$.apellidos").value("Parcial"))
                .andExpect(jsonPath("$.telefono").value("3001234567"));
    }

    // ── Estados ──────────────────────────────────────────────────────────────

    @Test
    void transicionValidaQuedaEnElHistorial() throws Exception {
        UUID id = crearMiembro("Transicion");

        mvc.perform(patch(BASE + "/" + id + "/estado").header("Authorization", pastor())
                        .contentType(MediaType.APPLICATION_JSON).content(cambioEstado("MIEMBRO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("MIEMBRO"));

        String hist = mvc.perform(get(BASE + "/" + id + "/historial-estado").header("Authorization", pastor()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode item = json.readTree(hist).path("historial").get(0);
        assertThat(item.path("estadoAnterior").asText()).isEqualTo("VISITOR");
        assertThat(item.path("estadoNuevo").asText()).isEqualTo("MIEMBRO");
    }

    @Test
    void transicionNoPermitidaSeRechaza() throws Exception {
        UUID id = crearMiembro("Invalida");
        // VISITOR → RESTAURADO no está en la máquina de estados
        mvc.perform(patch(BASE + "/" + id + "/estado").header("Authorization", pastor())
                        .contentType(MediaType.APPLICATION_JSON).content(cambioEstado("RESTAURADO")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void estadoDesconocidoSeRechaza() throws Exception {
        UUID id = crearMiembro("Desconocido");
        mvc.perform(patch(BASE + "/" + id + "/estado").header("Authorization", pastor())
                        .contentType(MediaType.APPLICATION_JSON).content(cambioEstado("JEFE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDACION_FALLIDA"));
    }

    @Test
    void secretariaNoPuedeCambiarEstados() throws Exception {
        UUID id = crearMiembro("SinPermiso");
        mvc.perform(patch(BASE + "/" + id + "/estado").header("Authorization", secretaria())
                        .contentType(MediaType.APPLICATION_JSON).content(cambioEstado("MIEMBRO")))
                .andExpect(status().isForbidden());
    }

    // ── Baja ─────────────────────────────────────────────────────────────────

    @Test
    void bajaEsLogicaYDejaAlMiembroInactivo() throws Exception {
        UUID id = crearMiembro("Baja");

        mvc.perform(delete(BASE + "/" + id).header("Authorization", pastor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"se mudó\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get(BASE + "/" + id).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    void bajaExigeMotivo() throws Exception {
        UUID id = crearMiembro("SinMotivo");
        mvc.perform(delete(BASE + "/" + id).header("Authorization", pastor())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Consulta ─────────────────────────────────────────────────────────────

    @Test
    void miembroInexistenteDevuelve404() throws Exception {
        mvc.perform(get(BASE + "/" + UUID.randomUUID()).header("Authorization", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void busquedaPorTextoEncuentraAlMiembro() throws Exception {
        crearMiembro("Zuluaga");
        String resp = mvc.perform(get(BASE + "/buscar").param("q", "zulua").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(resp).contains("Zuluaga").doesNotContain("SoloSedeB");
    }

    @Test
    void exportarExcelDevuelveUnArchivoXlsx() throws Exception {
        byte[] archivo = mvc.perform(get(BASE + "/exportar").param("formato", "EXCEL").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andReturn().getResponse().getContentAsByteArray();
        // Los .xlsx son archivos ZIP: empiezan con "PK"
        assertThat(archivo).hasSizeGreaterThan(100);
        assertThat(new String(archivo, 0, 2)).isEqualTo("PK");
    }
}
