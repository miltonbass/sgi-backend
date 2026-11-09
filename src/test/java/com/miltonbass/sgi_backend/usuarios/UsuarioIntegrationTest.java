package com.miltonbass.sgi_backend.usuarios;

import com.miltonbass.sgi_backend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Gestión de usuarios: un ADMIN_SEDE solo gestiona usuarios de su sede
 * y nunca puede tocar a un usuario con privilegios globales.
 */
class UsuarioIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/usuarios";
    private static final String NUEVA_PASSWORD = "{\"passwordNueva\":\"NuevaClave123\"}";

    private UUID globalEnSedeA;
    private UUID normalEnSedeA;
    private UUID normalEnSedeB;
    private String emailNormalA;

    @BeforeEach
    void usuarios() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        globalEnSedeA = crearUsuario("global" + sufijo, "global" + sufijo + "@sgi.test", sedeA, "ADMIN_GLOBAL");
        emailNormalA = "normala" + sufijo + "@sgi.test";
        normalEnSedeA = crearUsuario("normala" + sufijo, emailNormalA, sedeA, "SECRETARIA");
        normalEnSedeB = crearUsuario("normalb" + sufijo, "normalb" + sufijo + "@sgi.test", sedeB, "SECRETARIA");
    }

    private String adminSedeA() { return bearer(token(sedeA, SCHEMA_A, "ADMIN_SEDE")); }
    private String adminGlobal() { return bearer(token(sedeA, SCHEMA_A, "ADMIN_GLOBAL")); }

    private String nuevoUsuario(UUID sede, String rol) {
        String email = "nuevo" + UUID.randomUUID().toString().substring(0, 8) + "@sgi.test";
        return "{\"email\":\"" + email + "\",\"nombre\":\"Nuevo\",\"apellido\":\"Usuario\","
                + "\"sedeId\":\"" + sede + "\",\"roles\":[\"" + rol + "\"]}";
    }

    // ── Alta ─────────────────────────────────────────────────────────────────

    @Test
    void adminSedeCreaUsuarioEnSuSedePendienteDeActivacion() throws Exception {
        mvc.perform(post(BASE).header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuario(sedeA, "SECRETARIA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.debeActivar").value(true));
    }

    @Test
    void adminSedeNoPuedeCrearUnAdminGlobal() throws Exception {
        mvc.perform(post(BASE).header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuario(sedeA, "ADMIN_GLOBAL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ROL_NO_PERMITIDO"));
    }

    @Test
    void adminSedeNoPuedeCrearUsuariosEnOtraSede() throws Exception {
        mvc.perform(post(BASE).header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content(nuevoUsuario(sedeB, "SECRETARIA")))
                .andExpect(status().isForbidden());
    }

    // ── Listado ──────────────────────────────────────────────────────────────

    @Test
    void adminSedeSoloListaUsuariosDeSuSede() throws Exception {
        mvc.perform(get(BASE).header("Authorization", adminSedeA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios[*].id").value(org.hamcrest.Matchers.hasItem(normalEnSedeA.toString())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString(normalEnSedeB.toString()))));
    }

    @Test
    void adminSedeNoPuedeListarOtraSedePorParametro() throws Exception {
        mvc.perform(get(BASE).param("sedeId", sedeB.toString()).header("Authorization", adminSedeA()))
                .andExpect(status().isForbidden());
    }

    // ── Edición ──────────────────────────────────────────────────────────────

    @Test
    void adminSedeEditaUsuariosDeSuSede() throws Exception {
        mvc.perform(put(BASE + "/" + normalEnSedeA).header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"3110000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("3110000000"));
    }

    @Test
    void adminSedeNoPuedeDesactivarUsuariosDeOtraSede() throws Exception {
        mvc.perform(put(BASE + "/" + normalEnSedeB).header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSedeNoPuedeDesactivarAUnAdminGlobal() throws Exception {
        mvc.perform(put(BASE + "/" + globalEnSedeA).header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
    }

    // ── Reset de contraseña por admin ────────────────────────────────────────

    @Test
    void adminSedeReseteaPasswordDeUsuarioDeSuSede() throws Exception {
        mvc.perform(patch(BASE + "/" + normalEnSedeA + "/password").header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content(NUEVA_PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminSedeNoPuedeResetearPasswordDeUnAdminGlobal() throws Exception {
        // Escalada de privilegios: tomar la cuenta del admin global
        mvc.perform(patch(BASE + "/" + globalEnSedeA + "/password").header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content(NUEVA_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSedeNoPuedeResetearPasswordEnOtraSede() throws Exception {
        mvc.perform(patch(BASE + "/" + normalEnSedeB + "/password").header("Authorization", adminSedeA())
                        .contentType(MediaType.APPLICATION_JSON).content(NUEVA_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGlobalPuedeResetearEnCualquierSede() throws Exception {
        mvc.perform(patch(BASE + "/" + normalEnSedeB + "/password").header("Authorization", adminGlobal())
                        .contentType(MediaType.APPLICATION_JSON).content(NUEVA_PASSWORD))
                .andExpect(status().isNoContent());
    }

    // ── Cambio de contraseña propia ──────────────────────────────────────────

    @Test
    void cambioDePasswordPropiaExigeLaActualYPermiteEntrarConLaNueva() throws Exception {
        String propio = bearer(tokenPara(emailNormalA, normalEnSedeA, sedeA, SCHEMA_A, "SECRETARIA"));

        mvc.perform(patch(BASE + "/me/password").header("Authorization", propio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"incorrecta\",\"passwordNueva\":\"OtraClave123\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch(BASE + "/me/password").header("Authorization", propio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"OtraClave123\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailNormalA + "\",\"password\":\"OtraClave123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordNuevaDebeTenerAlMenos8Caracteres() throws Exception {
        String propio = bearer(tokenPara(emailNormalA, normalEnSedeA, sedeA, SCHEMA_A, "SECRETARIA"));
        mvc.perform(patch(BASE + "/me/password").header("Authorization", propio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"corta\"}"))
                .andExpect(status().isBadRequest());
    }
}
