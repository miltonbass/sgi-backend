-- ============================================================
-- SGI - Historia 1.3 - Template Schema por Sede
-- V3__sede_template_schema.sql
-- NOTA: Este archivo NO se ejecuta directamente.
--       Es el template que usa create_sede_schema() para
--       crear el schema de cada sede nueva.
-- ============================================================

-- ─── FUNCIÓN: crear schema para una sede nueva ──────────────
CREATE OR REPLACE FUNCTION shared.fn_crear_schema_sede(
    p_schema_name  VARCHAR,
    p_sede_id      UUID
)
RETURNS VOID AS $$
DECLARE
    v_sql TEXT;
BEGIN
    -- 1. Crear schema
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION sgi_admin', p_schema_name);

    -- 2. Permisos
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO sgi_app, sgi_readonly, sgi_backup', p_schema_name);
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE sgi_admin IN SCHEMA %I
         GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sgi_app', p_schema_name);
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE sgi_admin IN SCHEMA %I
         GRANT SELECT ON TABLES TO sgi_readonly, sgi_backup', p_schema_name);
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE sgi_admin IN SCHEMA %I
         GRANT USAGE, SELECT ON SEQUENCES TO sgi_app', p_schema_name);

    -- 3. Función updated_at en este schema
    EXECUTE format(
        'CREATE OR REPLACE FUNCTION %I.fn_set_updated_at()
         RETURNS TRIGGER AS $f$
         BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
         $f$ LANGUAGE plpgsql', p_schema_name);

    -- 4. Tablas del schema de sede

    -- MIEMBROS
    EXECUTE format('
        CREATE TABLE %I.miembros (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id             UUID NOT NULL DEFAULT %L::UUID,
            usuario_id          UUID REFERENCES shared.usuarios_sistema(id),
            numero_miembro      VARCHAR(20) UNIQUE,
            cedula              VARCHAR(20),
            nombres             VARCHAR(100) NOT NULL,
            apellidos           VARCHAR(100) NOT NULL,
            fecha_nacimiento    DATE,
            genero              VARCHAR(10),
            estado_civil        VARCHAR(20),
            telefono            VARCHAR(20),
            email               VARCHAR(150),
            direccion           TEXT,
            ciudad              VARCHAR(100),
            foto_url            VARCHAR(500),
            estado              VARCHAR(20) NOT NULL DEFAULT ''ACTIVO'',
            fecha_ingreso       DATE,
            fecha_bautismo      DATE,
            grupo_id            UUID,
            metadata            JSONB DEFAULT ''{}'',
            created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            deleted_at          TIMESTAMPTZ
        )', p_schema_name, p_sede_id);

    EXECUTE format(
        'CREATE INDEX idx_%s_miembros_cedula ON %I.miembros(cedula)',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE INDEX idx_%s_miembros_estado ON %I.miembros(estado)',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE TRIGGER trg_miembros_updated_at BEFORE UPDATE ON %I.miembros
         FOR EACH ROW EXECUTE FUNCTION %I.fn_set_updated_at()',
        p_schema_name, p_schema_name);

    -- GRUPOS / CÉLULAS
    EXECUTE format('
        CREATE TABLE %I.grupos (
            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id     UUID NOT NULL DEFAULT %L::UUID,
            nombre      VARCHAR(100) NOT NULL,
            tipo        VARCHAR(30) NOT NULL DEFAULT ''CELULA'',
            lider_id    UUID REFERENCES %I.miembros(id),
            descripcion TEXT,
            activo      BOOLEAN NOT NULL DEFAULT TRUE,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_sede_id, p_schema_name);

    -- FK grupos → miembros
    EXECUTE format(
        'ALTER TABLE %I.miembros ADD CONSTRAINT fk_miembro_grupo
         FOREIGN KEY (grupo_id) REFERENCES %I.grupos(id)',
        p_schema_name, p_schema_name);

    -- ASISTENCIAS
    EXECUTE format('
        CREATE TABLE %I.asistencias (
            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id     UUID NOT NULL DEFAULT %L::UUID,
            evento_id   UUID NOT NULL,
            miembro_id  UUID REFERENCES %I.miembros(id),
            presente    BOOLEAN NOT NULL DEFAULT TRUE,
            observacion TEXT,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_sede_id, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_asistencias_evento ON %I.asistencias(evento_id)',
        replace(p_schema_name, '-', '_'), p_schema_name);

    -- EVENTOS
    EXECUTE format('
        CREATE TABLE %I.eventos (
            id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id         UUID NOT NULL DEFAULT %L::UUID,
            titulo          VARCHAR(200) NOT NULL,
            tipo            VARCHAR(30) NOT NULL DEFAULT ''CULTO'',
            descripcion     TEXT,
            fecha_inicio    TIMESTAMPTZ NOT NULL,
            fecha_fin       TIMESTAMPTZ,
            lugar           VARCHAR(200),
            activo          BOOLEAN NOT NULL DEFAULT TRUE,
            created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_sede_id);

    -- FINANZAS - CUENTAS
    EXECUTE format('
        CREATE TABLE %I.cuentas_financieras (
            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id     UUID NOT NULL DEFAULT %L::UUID,
            nombre      VARCHAR(100) NOT NULL,
            tipo        VARCHAR(30) NOT NULL, -- BANCO, CAJA, DIGITAL
            banco       VARCHAR(100),
            numero      VARCHAR(50),
            saldo       NUMERIC(15,2) NOT NULL DEFAULT 0,
            activa      BOOLEAN NOT NULL DEFAULT TRUE,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_sede_id);

    -- FINANZAS - TRANSACCIONES
    EXECUTE format('
        CREATE TABLE %I.transacciones (
            id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id         UUID NOT NULL DEFAULT %L::UUID,
            cuenta_id       UUID REFERENCES %I.cuentas_financieras(id),
            miembro_id      UUID REFERENCES %I.miembros(id),
            tipo            VARCHAR(20) NOT NULL, -- INGRESO, EGRESO
            categoria       VARCHAR(50),          -- DIEZMO, OFRENDA, GASTO_OP...
            monto           NUMERIC(15,2) NOT NULL,
            descripcion     TEXT,
            comprobante_url VARCHAR(500),
            fecha           DATE NOT NULL DEFAULT CURRENT_DATE,
            registrado_por  UUID REFERENCES shared.usuarios_sistema(id),
            created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_sede_id, p_schema_name, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_transacciones_fecha ON %I.transacciones(fecha DESC)',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE INDEX idx_%s_transacciones_tipo ON %I.transacciones(tipo, fecha DESC)',
        replace(p_schema_name, '-', '_'), p_schema_name);

    -- AUDIT LOG por sede
    EXECUTE format('
        CREATE TABLE %I.audit_log_sede (
            id          BIGSERIAL PRIMARY KEY,
            usuario_id  UUID REFERENCES shared.usuarios_sistema(id),
            accion      VARCHAR(50) NOT NULL,
            entidad     VARCHAR(100),
            entidad_id  VARCHAR(100),
            datos_antes JSONB,
            datos_despues JSONB,
            ip_address  VARCHAR(45),
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name);

    -- Registrar en log
    INSERT INTO shared.audit_log (accion, entidad, entidad_id, descripcion, exitoso)
    VALUES ('CREATE_SCHEMA', 'SEDE', p_sede_id::TEXT,
            format('Schema %s creado exitosamente', p_schema_name), TRUE);

    RAISE NOTICE 'Schema % creado correctamente para sede %', p_schema_name, p_sede_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION shared.fn_crear_schema_sede IS
  'Crea el schema completo para una sede nueva. SECURITY DEFINER = ejecuta como sgi_admin';

GRANT EXECUTE ON FUNCTION shared.fn_crear_schema_sede(VARCHAR, UUID) TO sgi_admin;