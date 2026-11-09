package com.miltonbass.sgi_backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres 15 efímero (Docker) para los tests de integración.
 * Spring configura el DataSource automáticamente vía @ServiceConnection
 * y Flyway aplica todas las migraciones al arrancar el contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
                .withDatabaseName("sgi_system")
                .withUsername("sgi_admin")
                .withPassword("test_password");
    }
}
