package com.miltonbass.sgi_backend.support;

import com.miltonbass.sgi_backend.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

/**
 * Base para tests de integración: contexto Spring completo + MockMvc + Postgres real.
 *
 * Todas las subclases comparten el mismo contexto (y el mismo contenedor),
 * así que el arranque se paga una sola vez por ejecución.
 *
 * Datos de prueba (fixture) creados una sola vez:
 *   - Sede A: PAI_BOG (sede_pai_bog) — viene del seed de Flyway
 *   - Sede B: PAI_TST (sede_pai_tst) — creada aquí con fn_crear_schema_sede
 *   - Un miembro distinto en cada sede (para probar aislamiento)
 *   - Usuarios de login con contraseña conocida
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final String SCHEMA_A = "sede_pai_bog";
    protected static final String SCHEMA_B = "sede_pai_tst";
    protected static final String PASSWORD = "Test1234!";
    protected static final String EMAIL_ADMIN = "admin.sede@sgi.test";
    protected static final String EMAIL_BLOQUEO = "bloqueo@sgi.test";

    protected static UUID sedeA;
    protected static UUID sedeB;
    protected static UUID miembroA;
    protected static UUID miembroB;
    protected static UUID adminUserId;

    private static boolean fixtureLista = false;

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JwtService jwtService;

    @BeforeEach
    void prepararFixture() {
        synchronized (IntegrationTestBase.class) {
            if (fixtureLista) return;

            sedeA = jdbc.queryForObject(
                    "SELECT id FROM shared.sedes WHERE schema_name = ?", UUID.class, SCHEMA_A);

            jdbc.update("INSERT INTO shared.sedes (codigo, nombre, schema_name, ciudad, activa) "
                    + "VALUES ('PAI_TST', 'PAI Test', ?, 'Test', true)", SCHEMA_B);
            sedeB = jdbc.queryForObject(
                    "SELECT id FROM shared.sedes WHERE schema_name = ?", UUID.class, SCHEMA_B);
            jdbc.query("SELECT shared.fn_crear_schema_sede(?, ?)",
                    ps -> { ps.setString(1, SCHEMA_B); ps.setObject(2, sedeB); }, rs -> null);

            miembroA = jdbc.queryForObject("INSERT INTO " + SCHEMA_A + ".miembros (nombres, apellidos) "
                    + "VALUES ('Ana', 'SoloSedeA') RETURNING id", UUID.class);
            miembroB = jdbc.queryForObject("INSERT INTO " + SCHEMA_B + ".miembros (nombres, apellidos) "
                    + "VALUES ('Beto', 'SoloSedeB') RETURNING id", UUID.class);

            // Una alerta y una tarea de consolidación en sede A (para tests de filtros/inyección)
            jdbc.update("INSERT INTO " + SCHEMA_A + ".alertas_ausencia (sede_id, miembro_id, semanas_ausente) "
                    + "VALUES (?, ?, 3)", sedeA, miembroA);
            jdbc.update("INSERT INTO " + SCHEMA_A + ".tareas_consolidacion (sede_id, miembro_id, descripcion) "
                    + "VALUES (?, ?, 'Llamar a Ana')", sedeA, miembroA);

            adminUserId = crearUsuario("admin.sede", EMAIL_ADMIN, sedeA, "ADMIN_SEDE");
            crearUsuario("bloqueo", EMAIL_BLOQUEO, sedeA, "SECRETARIA");

            fixtureLista = true;
        }
    }

    /** Crea un usuario activo con contraseña PASSWORD y los roles indicados en la sede. */
    protected UUID crearUsuario(String username, String email, UUID sede, String... roles) {
        String hash = new BCryptPasswordEncoder().encode(PASSWORD);
        UUID id = jdbc.queryForObject("INSERT INTO shared.usuarios_sistema "
                + "(username, email, password_hash, nombre, apellido, activo) "
                + "VALUES (?, ?, ?, 'Test', 'Usuario', true) RETURNING id",
                UUID.class, username, email, hash);
        jdbc.update("INSERT INTO shared.usuarios_sedes (usuario_id, sede_id, roles, activo) "
                + "VALUES (?, ?, ?::text[], true)", id, sede, "{" + String.join(",", roles) + "}");
        return id;
    }

    /** Access token firmado para la sede/roles indicados (sin pasar por login). */
    protected String token(UUID sede, String schema, String... roles) {
        return jwtService.generarAccessToken(EMAIL_ADMIN, adminUserId.toString(),
                sede != null ? sede.toString() : null, schema, List.of(roles));
    }

    /** Access token para un usuario concreto. */
    protected String tokenPara(String email, UUID userId, UUID sede, String schema, String... roles) {
        return jwtService.generarAccessToken(email, userId.toString(),
                sede != null ? sede.toString() : null, schema, List.of(roles));
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
