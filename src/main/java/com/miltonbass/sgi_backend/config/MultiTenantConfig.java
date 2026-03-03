// src/main/java/com/miltonbass/sgi_backend/config/MultiTenantConfig.java
package com.miltonbass.sgi_backend.config;

import com.miltonbass.sgi_backend.infrastructure.persistence.SgiConnectionProvider;
import com.miltonbass.sgi_backend.infrastructure.persistence.SchemaBasedTenantResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiTenantConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            SgiConnectionProvider connectionProvider,
            SchemaBasedTenantResolver tenantResolver) {

        return properties -> {
            properties.put(
                AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER,
                connectionProvider
            );
            properties.put(
                AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                tenantResolver
            );
            properties.put("hibernate.multiTenancy", "SCHEMA");
        };
    }
}