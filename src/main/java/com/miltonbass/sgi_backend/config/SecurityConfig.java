package com.miltonbass.sgi_backend.config;

import com.miltonbass.sgi_backend.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
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

                // Health check para monitoreo de infraestructura
                .requestMatchers("/actuator/health").permitAll()

                // Todo lo demás requiere autenticación válida
                .anyRequest().authenticated()
            )

            // Insertar JwtAuthFilter antes del filtro estándar de usuario/contraseña
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS: permite requests desde el frontend Angular.
     * En producción el origen se configura via variables de entorno o application-prod.properties.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Orígenes permitidos (dev + prod)
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:4200",       // Angular dev
                "http://localhost:4201",       // Angular segundo puerto
                "https://*.iglesiapaibog.com", // Producción (ajustar dominio real)
                "https://sgi.iglesiapaibog.com"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Sede-Id"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Pre-flight cache 1 hora

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}