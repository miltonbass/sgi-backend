// src/main/java/com/miltonbass/sgi_backend/infrastructure/web/TenantFilter.java
package com.miltonbass.sgi_backend.infrastructure.web;

import com.miltonbass.sgi_backend.config.TenantContext;
import com.miltonbass.sgi_backend.security.JwtAuthFilter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Intercepta cada request y establece el TenantContext
 * usando el atributo "sgi_sede_schema" que pone el JwtAuthFilter
 * a partir del token validado. Sin token con sede → schema "shared".
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

        // Única fuente válida: el claim sedeSchema del JWT firmado (puesto por JwtAuthFilter).
        // No se acepta ningún header del cliente: permitiría saltar a otra sede.
        String sedeSchema = (String) httpRequest.getAttribute(JwtAuthFilter.ATTR_SEDE_SCHEMA);

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