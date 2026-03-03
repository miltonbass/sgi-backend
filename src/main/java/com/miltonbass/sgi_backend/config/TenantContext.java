// src/main/java/com/miltonbass/sgi_backend/config/TenantContext.java
package com.miltonbass.sgi_backend.config;

/**
 * Almacena el schema del tenant actual en un ThreadLocal.
 * Se setea en TenantFilter después de validar el JWT.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT =
            new InheritableThreadLocal<>();

    public static final String SHARED_SCHEMA  = "shared";
    public static final String DEFAULT_SCHEMA = "shared";

    private TenantContext() {}

    public static void setCurrentTenant(String tenant) {
        CURRENT_TENANT.set(tenant);
    }

    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        return (tenant != null && !tenant.isBlank()) ? tenant : DEFAULT_SCHEMA;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}