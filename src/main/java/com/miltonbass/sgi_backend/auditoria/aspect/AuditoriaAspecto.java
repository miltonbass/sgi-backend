package com.miltonbass.sgi_backend.auditoria.aspect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miltonbass.sgi_backend.auditoria.service.AuditoriaGeneralService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Aspect
@Component
@Order(100)
public class AuditoriaAspecto {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAspecto.class);

    private static final Set<String> CAMPOS_SENSIBLES = Set.of(
            "password", "passwordHash", "passwordNueva", "passwordActual",
            "token", "refreshToken", "secret", "apiKey"
    );

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE
    );

    private final AuditoriaGeneralService auditoriaService;
    private final ObjectMapper mapper;

    public AuditoriaAspecto(AuditoriaGeneralService auditoriaService, ObjectMapper mapper) {
        this.auditoriaService = auditoriaService;
        this.mapper           = mapper;
    }

    // Intercepta todos los controllers excepto auth y el propio módulo de auditoría
    @Pointcut("within(com.miltonbass.sgi_backend..*Controller+) " +
              "&& !within(com.miltonbass.sgi_backend.auth..*) " +
              "&& !within(com.miltonbass.sgi_backend.auditoria..*)")
    public void controladores() {}

    @Around("controladores()")
    public Object auditar(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest req = obtenerRequest();
        if (req == null || esLectura(req.getMethod())) {
            return pjp.proceed();
        }

        String resultado = "EXITOSO";
        String errorMsg  = null;
        try {
            return pjp.proceed();
        } catch (AccessDeniedException e) {
            resultado = "DENEGADO";
            errorMsg  = e.getMessage();
            throw e;
        } catch (Exception e) {
            resultado = "ERROR";
            errorMsg  = e.getMessage();
            throw e;
        } finally {
            try {
                registrar(req, pjp.getArgs(), resultado, errorMsg);
            } catch (Exception e) {
                log.error("[Auditoria] {}", e.getMessage());
            }
        }
    }

    private boolean esLectura(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private void registrar(HttpServletRequest req, Object[] args,
                           String resultado, String errorMsg) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;

        UUID   userId = null;
        UUID   sedeId = null;
        String email  = auth.getName();

        if (auth.getDetails() instanceof Claims claims) {
            String uid = claims.get("userId", String.class);
            if (uid != null) userId = UUID.fromString(uid);
            String sid = claims.get("sedeId", String.class);
            if (sid != null) sedeId = UUID.fromString(sid);
        }

        String path      = req.getRequestURI();
        String modulo    = detectarModulo(path);
        String accion    = detectarAccion(req.getMethod());
        String entidadId = extraerEntidadId(path);
        Map<String, Object> detalle = construirDetalle(args);
        String ip = obtenerIp(req);

        auditoriaService.registrar(
                userId, email, sedeId,
                modulo, accion, entidadId,
                path, req.getMethod(),
                detalle, ip, resultado, errorMsg
        );
    }

    private String detectarModulo(String path) {
        if (path.contains("/asistencias"))   return "ASISTENCIAS";
        if (path.contains("/resumen"))       return "REPORTES";
        if (path.contains("/exportar"))      return "REPORTES";
        if (path.contains("/eventos"))       return "EVENTOS";
        if (path.contains("/sesiones"))      return "SESIONES";
        if (path.contains("/grupos"))        return "GRUPOS";
        if (path.contains("/alertas"))       return "ALERTAS";
        if (path.contains("/consolidacion")) return "CONSOLIDACION";
        if (path.contains("/miembros"))      return "MIEMBROS";
        if (path.contains("/usuarios"))      return "USUARIOS";
        if (path.contains("/sedes"))         return "SEDES";
        if (path.contains("/configuracion")) return "CONFIGURACION";
        return "GENERAL";
    }

    private String detectarAccion(String method) {
        return switch (method) {
            case "POST"   -> "CREAR";
            case "PUT"    -> "ACTUALIZAR";
            case "PATCH"  -> "ACTUALIZAR";
            case "DELETE" -> "ELIMINAR";
            default       -> method;
        };
    }

    private String extraerEntidadId(String path) {
        var matcher = UUID_PATTERN.matcher(path);
        return matcher.find() ? matcher.group() : null;
    }

    private Map<String, Object> construirDetalle(Object[] args) {
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof Authentication || arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse) continue;
            if (arg instanceof String || arg instanceof UUID
                    || arg instanceof Number || arg instanceof Boolean) continue;
            try {
                String json = mapper.writeValueAsString(arg);
                Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
                eliminarCamposSensibles(map);
                return map;
            } catch (Exception ignored) {}
        }
        return Map.of();
    }

    private void eliminarCamposSensibles(Map<String, Object> map) {
        CAMPOS_SENSIBLES.forEach(map::remove);
        map.values().forEach(v -> {
            if (v instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) v;
                eliminarCamposSensibles(nested);
            }
        });
    }

    private String obtenerIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private HttpServletRequest obtenerRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();
        } catch (Exception e) {
            return null;
        }
    }
}
