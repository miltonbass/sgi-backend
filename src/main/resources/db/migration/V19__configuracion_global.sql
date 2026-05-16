-- V19: Tabla de configuracion global del sistema (SMTP, seguridad, etc.)
-- Los datos por sede se almacenan en shared.sedes.config JSONB existente.

CREATE TABLE IF NOT EXISTS shared.configuracion_global (
    dominio    VARCHAR(50) PRIMARY KEY,
    config     JSONB       NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID        REFERENCES shared.usuarios_sistema(id)
);
