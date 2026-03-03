package com.miltonbass.sgi_backend.infrastructure.persistence;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class SgiConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public SgiConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        if (!tenantIdentifier.matches("^[a-zA-Z][a-zA-Z0-9_]{0,62}$")) {
            throw new SQLException("Tenant inválido: " + tenantIdentifier);
        }
        Connection conn = dataSource.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "SET search_path TO " + tenantIdentifier + ", shared, public"
            );
        }
        return conn;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection)
            throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO shared, public");
        }
        connection.close();
    }

    @Override public boolean supportsAggressiveRelease() { return false; }
    @Override public boolean isUnwrappableAs(Class<?> u) { return false; }
    @Override public <T> T unwrap(Class<T> u) { return null; }
}