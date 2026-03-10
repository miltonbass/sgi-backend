package com.miltonbass.sgi_backend.security;

import com.miltonbass.sgi_backend.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT que se ejecuta una vez por request.
 *
 * Responsabilidades:
 *   1. Extraer el Bearer token del header Authorization
 *   2. Validar la firma y expiración con JwtService
 *   3. Setear el Authentication en el SecurityContext
 *   4. Poner el atributo "sgi_sede_schema" en el request
 *      para que TenantFilter (Historia 1.3) lo tome y configure el schema Postgres
 *
 * Si el token está ausente o es inválido, el filtro simplemente NO setea
 * el Authentication. Spring Security rechazará la request con 401 si la
 * ruta está protegida.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** Nombre del atributo de request que TenantFilter lee para el schema */
    public static final String ATTR_SEDE_SCHEMA = "sgi_sede_schema";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extraerToken(request);

        if (token != null) {
            procesarToken(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private void procesarToken(String token, HttpServletRequest request) {
        try {
            Claims claims = jwtService.validarYObtenerClaims(token);

            String email = claims.getSubject();
            String sedeSchema = claims.get("sedeSchema", String.class);

            // Construir authorities a partir de los roles del JWT
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles");
            List<SimpleGrantedAuthority> authorities = roles == null
                    ? List.of()
                    : roles.stream()
                           .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                           .toList();

            // Setear Authentication en el SecurityContext
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(email, null, authorities);
            auth.setDetails(claims);  // Claims disponibles vía SecurityContext para los controllers
            SecurityContextHolder.getContext().setAuthentication(auth);

            // ⬇ Punto de integración con TenantFilter (Historia 1.3)
            // TenantFilter lee este atributo para hacer SET search_path en Postgres
            if (sedeSchema != null && !sedeSchema.isBlank()) {
                request.setAttribute(ATTR_SEDE_SCHEMA, sedeSchema);
                log.debug("Schema de sede establecido: {}", sedeSchema);
            }

        } catch (JwtException e) {
            // Token inválido o expirado → no setear auth, Spring Security rechazará si la ruta lo requiere
            log.debug("JWT inválido: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.error("Error procesando JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Extrae el token del header Authorization: Bearer <token>
     */
    private String extraerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /**
     * No ejecutar el filtro en las rutas públicas de auth para mayor eficiencia.
     * Igual funcionaría sin esto, pero evita parsing innecesario.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/login")
            || path.equals("/api/auth/refresh")
            || path.startsWith("/actuator/health");
    }
}