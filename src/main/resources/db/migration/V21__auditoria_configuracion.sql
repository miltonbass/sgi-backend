-- H7.8 — Auditoría de cambios de configuración
CREATE TABLE IF NOT EXISTS shared.auditoria_configuracion (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    sede_id       UUID        NOT NULL REFERENCES shared.sedes(id),
    usuario_id    UUID        NOT NULL,
    usuario_email VARCHAR(150) NOT NULL,
    seccion       VARCHAR(50) NOT NULL,
    accion        VARCHAR(50) NOT NULL,
    detalle       JSONB,
    realizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_auditoria_sede_fecha
    ON shared.auditoria_configuracion (sede_id, realizado_en DESC);

CREATE INDEX IF NOT EXISTS idx_auditoria_sede_seccion
    ON shared.auditoria_configuracion (sede_id, seccion);
