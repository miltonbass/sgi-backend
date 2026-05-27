-- H8: Auditoría general de plataforma — todas las operaciones de escritura
CREATE TABLE IF NOT EXISTS shared.auditoria_general (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    usuario_id      UUID,
    usuario_email   VARCHAR(150),
    sede_id         UUID,
    modulo          VARCHAR(50)  NOT NULL,
    accion          VARCHAR(30)  NOT NULL,
    entidad_id      VARCHAR(100),
    endpoint        VARCHAR(500),
    metodo_http     VARCHAR(10),
    detalle         JSONB,
    ip              VARCHAR(50),
    resultado       VARCHAR(20)  NOT NULL DEFAULT 'EXITOSO',
    error_mensaje   TEXT,
    realizado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_auditoria_gen_email  ON shared.auditoria_general(usuario_email, realizado_en DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_gen_sede   ON shared.auditoria_general(sede_id,       realizado_en DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_gen_modulo ON shared.auditoria_general(modulo,        realizado_en DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_gen_fecha  ON shared.auditoria_general(realizado_en DESC);
