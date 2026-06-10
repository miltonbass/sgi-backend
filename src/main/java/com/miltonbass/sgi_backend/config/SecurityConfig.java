package com.miltonbass.sgi_backend.config;

import com.miltonbass.sgi_backend.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.miltonbass.sgi_backend.configuracion.service.ConfiguracionDominioService;

import java.util.List;

/**
 * Configuración de Spring Security — completamente stateless (sin sesión HTTP).
 *
 * Rutas públicas:
 *   POST /api/auth/login     → login
 *   POST /api/auth/refresh   → renovar token
 *   POST /api/auth/logout    → cerrar sesión (requiere refresh token, no JWT)
 *   GET  /actuator/health    → health check de infraestructura
 *
 * Todo lo demás requiere Bearer token válido.
 * El control de roles granular se hace con @PreAuthorize en los controllers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // Habilita @PreAuthorize, @PostAuthorize, @Secured
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ConfiguracionDominioService dominioService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ConfiguracionDominioService dominioService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.dominioService = dominioService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Sin CSRF porque usamos JWT (no cookies de sesión)
            .csrf(AbstractHttpConfigurer::disable)

            // CORS configurado para Angular en localhost:4200 y producción
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Sin sesión HTTP — 100% stateless
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Reglas de autorización
            .authorizeHttpRequests(auth -> auth
                // Rutas públicas de autenticación
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/auth/sedes").permitAll()

                // Health check para monitoreo de infraestructura
                .requestMatchers("/actuator/health").permitAll()

                // Todo lo demás requiere autenticación válida
                .anyRequest().authenticated()
            )

            // 401 para token ausente/inválido/expirado, 403 para rol insuficiente
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
            )

            // Insertar JwtAuthFilter antes del filtro estándar de usuario/contraseña
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Jerarquía de roles: ADMIN_GLOBAL hereda todos los permisos de ADMIN_SEDE.
     * Cualquier @PreAuthorize que permita ADMIN_SEDE permite automáticamente ADMIN_GLOBAL,
     * sin necesidad de declararlo explícitamente en cada anotación.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN_GLOBAL").implies("ADMIN_SEDE")
                .build();
    }

    /**
     * Aplica la jerarquía de roles a @PreAuthorize y @PostAuthorize (method security).
     * Debe ser static para evitar conflictos de ciclo de vida con el proxy de @Configuration.
     */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    /**
     * CORS: permite requests desde el frontend Angular.
     * En producción el origen se configura via variables de entorno o application-prod.properties.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration config = new CorsConfiguration();

            // Orígenes por defecto (desarrollo local)
            config.addAllowedOriginPattern("http://localhost:4200");
            config.addAllowedOriginPattern("http://localhost:4201");
            config.addAllowedOriginPattern("http://localhost:8080");

            // Cargar orígenes dinámicamente desde la base de datos
            try {
                String corsOrigenes = dominioService.getCorsOrigenes();
                if (corsOrigenes != null && !corsOrigenes.isBlank()) {
                    for (String origin : corsOrigenes.split(",")) {
                        String trimmed = origin.trim();
                        // Remover barra diagonal al final si existe (los navegadores envían el Origin sin ella)
                        if (trimmed.endsWith("/")) {
                            trimmed = trimmed.substring(0, trimmed.length() - 1);
                        }
                        if (!trimmed.isEmpty()) {
                            config.addAllowedOriginPattern(trimmed);
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback seguro a los dominios originales si la BD no está lista o falla
                config.addAllowedOriginPattern("https://*.iglesiapaibog.com");
                config.addAllowedOriginPattern("https://sgi.iglesiapaibog.com");
                config.addAllowedOriginPattern("https://membresia.jovenescristianos.co");
            }

            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Sede-Id"));
            config.setExposedHeaders(List.of("Authorization"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L); // Pre-flight cache 1 hora

            return config;
        };
    }
}