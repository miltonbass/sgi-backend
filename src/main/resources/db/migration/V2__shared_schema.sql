-- ============================================================
-- SGI - Historia 1.3 - Schema Compartido
-- V2__shared_schema.sql
-- ============================================================
SET search_path TO shared, public;

-- ─── FUNCIÓN updated_at ─────────────────────────────────────
CREATE OR REPLACE FUNCTION shared.fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

-- ─── SEDES ──────────────────────────────────────────────────
CREATE TABLE shared.sedes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    codigo          VARCHAR(20)  NOT NULL UNIQUE,
    nombre          VARCHAR(100) NOT NULL,
    schema_name     VARCHAR(63)  NOT NULL UNIQUE, -- 'sede_pai_bog'
    descripcion     TEXT,
    ciudad          VARCHAR(100),
    departamento    VARCHAR(100),
    pais            VARCHAR(50)  DEFAULT 'Colombia',
    direccion       TEXT,
    telefono        VARCHAR(20),
    email           VARCHAR(150),
    logo_url        VARCHAR(500),
    zona_horaria    VARCHAR(50)  DEFAULT 'America/Bogota',
    activa          BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_fundacion DATE,
    config          JSONB        DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_sedes_codigo      ON shared.sedes(codigo);
CREATE INDEX idx_sedes_schema_name ON shared.sedes(schema_name);
CREATE INDEX idx_sedes_activa      ON shared.sedes(activa) WHERE activa = TRUE;

CREATE TRIGGER trg_sedes_updated_at BEFORE UPDATE ON shared.sedes
  FOR EACH ROW EXECUTE FUNCTION shared.fn_set_updated_at();

-- ─── USUARIOS SISTEMA ───────────────────────────────────────
CREATE TABLE shared.usuarios_sistema (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    username                VARCHAR(50)  NOT NULL UNIQUE,
    email                   VARCHAR(150) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,    -- BCrypt $2a$
    nombre                  VARCHAR(100) NOT NULL,
    apellido                VARCHAR(100) NOT NULL,
    telefono                VARCHAR(20),
    avatar_url              VARCHAR(500),
    activo                  BOOLEAN      NOT NULL DEFAULT TRUE,
    super_admin             BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Seguridad (Historia 1.4)
    intentos_fallidos       SMALLINT     NOT NULL DEFAULT 0,
    bloqueado_hasta         TIMESTAMPTZ,
    ultimo_login            TIMESTAMPTZ,
    ultimo_login_sede_id    UUID         REFERENCES shared.sedes(id),
    debe_cambiar_password   BOOLEAN      NOT NULL DEFAULT FALSE,
    token_reset_password    VARCHAR(255),
    token_reset_expira      TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE INDEX idx_usuarios_email    ON shared.usuarios_sistema(email);
CREATE INDEX idx_usuarios_username ON shared.usuarios_sistema(username);
CREATE INDEX idx_usuarios_activo   ON shared.usuarios_sistema(activo) WHERE activo = TRUE;

CREATE TRIGGER trg_usuarios_updated_at BEFORE UPDATE ON shared.usuarios_sistema
  FOR EACH ROW EXECUTE FUNCTION shared.fn_set_updated_at();

-- ─── USUARIOS ↔ SEDES (multi-rol) ───────────────────────────
CREATE TABLE shared.usuarios_sedes (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id  UUID         NOT NULL REFERENCES shared.usuarios_sistema(id) ON DELETE CASCADE,
    sede_id     UUID         NOT NULL REFERENCES shared.sedes(id)            ON DELETE CASCADE,
    roles       TEXT[]       NOT NULL DEFAULT '{}', -- {ADMIN,PASTOR,SECRETARIO,TESORERO,MIEMBRO}
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(usuario_id, sede_id)
);

CREATE INDEX idx_us_usuario ON shared.usuarios_sedes(usuario_id);
CREATE INDEX idx_us_sede    ON shared.usuarios_sedes(sede_id);

-- ─── REFRESH TOKENS ─────────────────────────────────────────
CREATE TABLE shared.refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id  UUID         NOT NULL REFERENCES shared.usuarios_sistema(id) ON DELETE CASCADE,
    sede_id     UUID         REFERENCES shared.sedes(id)                     ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,    -- SHA-256 del token real
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    expira_en   TIMESTAMPTZ  NOT NULL,
    revocado_en TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rt_usuario ON shared.refresh_tokens(usuario_id);
CREATE INDEX idx_rt_token   ON shared.refresh_tokens(token_hash);
CREATE INDEX idx_rt_activo  ON shared.refresh_tokens(activo, expira_en);

-- ─── AUDIT LOG (particionado por año) ───────────────────────
CREATE TABLE shared.audit_log (
    id          BIGSERIAL,
    usuario_id  UUID         REFERENCES shared.usuarios_sistema(id),
    sede_id     UUID         REFERENCES shared.sedes(id),
    accion      VARCHAR(50)  NOT NULL, -- LOGIN, LOGOUT, CREATE, UPDATE, DELETE
    entidad     VARCHAR(100),
    entidad_id  VARCHAR(100),
    descripcion TEXT,
    ip_address  VARCHAR(45),
    exitoso     BOOLEAN      NOT NULL DEFAULT TRUE,
    metadata    JSONB        DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE shared.audit_log_2025 PARTITION OF shared.audit_log
  FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
CREATE TABLE shared.audit_log_2026 PARTITION OF shared.audit_log
  FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE shared.audit_log_2027 PARTITION OF shared.audit_log
  FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');

CREATE INDEX idx_audit_usuario ON shared.audit_log(usuario_id, created_at DESC);
CREATE INDEX idx_audit_sede    ON shared.audit_log(sede_id,    created_at DESC);
CREATE INDEX idx_audit_accion  ON shared.audit_log(accion,     created_at DESC);

-- ─── CONFIGURACIÓN SISTEMA ──────────────────────────────────
CREATE TABLE shared.configuracion_sistema (
    clave       VARCHAR(100) PRIMARY KEY,
    valor       TEXT         NOT NULL,
    descripcion TEXT,
    tipo        VARCHAR(20)  DEFAULT 'STRING', -- STRING, INTEGER, BOOLEAN, JSON
    editable    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_config_updated_at BEFORE UPDATE ON shared.configuracion_sistema
  FOR EACH ROW EXECUTE FUNCTION shared.fn_set_updated_at();