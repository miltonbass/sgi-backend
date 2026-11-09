package com.miltonbass.sgi_backend.security;

import com.miltonbass.sgi_backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Barrido de seguridad sobre TODOS los endpoints /api/** registrados en Spring MVC.
 * Si alguien agrega un endpoint nuevo sin proteger, estos tests fallan.
 */
class EndpointSecurityIntegrationTest extends IntegrationTestBase {

    /** Endpoints intencionalmente públicos (sin token). */
    private static final Set<String> PUBLICOS = Set.of(
            "POST /api/auth/login",
            "POST /api/auth/refresh",
            "POST /api/auth/logout",
            "GET /api/auth/sedes");

    /**
     * Endpoints para cualquier usuario autenticado (sin restricción de rol), revisados a propósito.
     * Agregar aquí solo tras confirmar que no exponen datos sensibles.
     */
    private static final Set<String> SOLO_AUTENTICADO = Set.of(
            "GET /api/v1/configuracion/logo",          // branding de la sede
            "GET /api/v1/configuracion/logo/compacto", // branding de la sede
            "PATCH /api/v1/usuarios/me/password");     // cada usuario cambia la suya

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    record Endpoint(HttpMethod method, String pattern, HandlerMethod handler) {
        String key() { return method.name() + " " + pattern; }

        String url() { return pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString()); }

        boolean declaraPreAuthorize() {
            return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class);
        }

        /** Endpoints cuyos argumentos obligatorios se validan antes que @PreAuthorize. */
        boolean tieneEntradaObligatoria() {
            for (MethodParameter p : handler.getMethodParameters()) {
                if (p.hasParameterAnnotation(RequestBody.class) || p.hasParameterAnnotation(RequestPart.class)) return true;
                RequestParam rp = p.getParameterAnnotation(RequestParam.class);
                if (rp != null && rp.required() && rp.defaultValue().equals(
                        org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE)) return true;
            }
            return false;
        }
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> lista = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            if (info.getPathPatternsCondition() == null) continue;
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/")) continue;
                if (metodos.isEmpty()) {
                    lista.add(new Endpoint(HttpMethod.GET, pattern, e.getValue()));
                } else {
                    for (RequestMethod m : metodos) {
                        lista.add(new Endpoint(HttpMethod.valueOf(m.name()), pattern, e.getValue()));
                    }
                }
            }
        }
        lista.sort(Comparator.comparing(Endpoint::key));
        return lista;
    }

    private MockHttpServletRequestBuilder req(Endpoint ep) {
        MockHttpServletRequestBuilder b = request(ep.method(), ep.url());
        if (ep.method() == HttpMethod.POST || ep.method() == HttpMethod.PUT || ep.method() == HttpMethod.PATCH) {
            b.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return b;
    }

    @Test
    void seDescubrenLosEndpoints() {
        assertThat(endpoints()).hasSizeGreaterThan(50);
    }

    @Test
    void sinTokenTodoEndpointPrivadoDevuelve401() throws Exception {
        List<String> fallos = new ArrayList<>();
        for (Endpoint ep : endpoints()) {
            if (PUBLICOS.contains(ep.key())) continue;
            int st = mvc.perform(req(ep)).andReturn().getResponse().getStatus();
            if (st != 401) fallos.add(ep.key() + " -> " + st);
        }
        assertThat(fallos).as("Endpoints privados accesibles sin token").isEmpty();
    }

    @Test
    void tokenFalsificadoDevuelve401() throws Exception {
        String valido = token(sedeA, SCHEMA_A, "ADMIN_GLOBAL");
        String falso = valido.substring(0, valido.length() - 4) + "AAAA";
        List<String> fallos = new ArrayList<>();
        for (Endpoint ep : endpoints()) {
            if (PUBLICOS.contains(ep.key())) continue;
            int st = mvc.perform(req(ep).header("Authorization", bearer(falso)))
                    .andReturn().getResponse().getStatus();
            if (st != 401) fallos.add(ep.key() + " -> " + st);
        }
        assertThat(fallos).as("Endpoints que aceptan un token con firma alterada").isEmpty();
    }

    @Test
    void todoEndpointPrivadoDeclaraPreAuthorize() {
        List<String> sinRegla = endpoints().stream()
                .filter(ep -> !PUBLICOS.contains(ep.key()) && !SOLO_AUTENTICADO.contains(ep.key()))
                .filter(ep -> !ep.declaraPreAuthorize())
                .map(Endpoint::key)
                .toList();
        assertThat(sinRegla).as("Endpoints sin @PreAuthorize (cualquier usuario autenticado entra)").isEmpty();
    }

    @Test
    void rolSinPermisosRecibe403() throws Exception {
        String sinPermisos = bearer(token(sedeA, SCHEMA_A, "ROL_SIN_PERMISOS"));
        List<String> fallos = new ArrayList<>();
        for (Endpoint ep : endpoints()) {
            if (PUBLICOS.contains(ep.key()) || !ep.declaraPreAuthorize()) continue;
            int st = mvc.perform(req(ep).header("Authorization", sinPermisos))
                    .andReturn().getResponse().getStatus();
            boolean ok = st == 403 || (ep.tieneEntradaObligatoria() && st >= 400 && st < 500);
            if (!ok) fallos.add(ep.key() + " -> " + st);
        }
        assertThat(fallos).as("Endpoints que no rechazan a un rol sin permisos").isEmpty();
    }
}
