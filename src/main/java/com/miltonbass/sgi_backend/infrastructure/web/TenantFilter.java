// src/main/java/com/miltonbass/sgi_backend/infrastructure/web/TenantFilter.java
package com.miltonbass.sgi_backend.infrastructure.web;

import com.miltonbass.sgi_backend.config.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Intercepta cada request y establece el TenantContext
 * usando el atributo "sgi_sede_schema" que pondrá el JwtAuthFilter
 * en la Historia 1.4.
 *
 * Por ahora lee el header X-Sede-Schema para pruebas.
 */
@Component
@Order(2)
public class TenantFilter implements Filter {

    // Patrón seguro: sede_pai_bog, sede_med_001, etc.
    private static final String SCHEMA_PATTERN = "^sede_[a-z0-9_]{1,50}$";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Prioridad 1: atributo puesto por JwtAuthFilter (Historia 1.4)
        String sedeSchema = (String) httpRequest.getAttribute("sgi_sede_schema");

        // Prioridad 2 (solo dev): header manual para tests sin JWT
        if (sedeSchema == null) {
            sedeSchema = httpRequest.getHeader("X-Sede-Schema");
        }

        // Validar y setear
        if (sedeSchema != null && sedeSchema.matches(SCHEMA_PATTERN)) {
            TenantContext.setCurrentTenant(sedeSchema);
        } else {
            TenantContext.setCurrentTenant(TenantContext.DEFAULT_SCHEMA);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear(); // CRÍTICO: evita memory leaks en thread pool
        }
    }
}