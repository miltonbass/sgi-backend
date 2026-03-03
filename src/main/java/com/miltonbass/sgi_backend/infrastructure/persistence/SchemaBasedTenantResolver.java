// src/main/java/com/miltonbass/sgi_backend/infrastructure/persistence/SchemaBasedTenantResolver.java
package com.miltonbass.sgi_backend.infrastructure.persistence;

import com.miltonbass.sgi_backend.config.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class SchemaBasedTenantResolver
        implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentTenant(); // Nunca retorna null
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}