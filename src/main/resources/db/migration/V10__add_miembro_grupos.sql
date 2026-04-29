-- ============================================================================
-- V10 :: H2.3 - Tabla miembro_grupos (relación N:M miembros <-> grupos)
--       y actualización de fn_crear_schema_sede
-- ============================================================================

DO $$
DECLARE
    v_schema TEXT;
BEGIN
    FOR v_schema IN
        SELECT schema_name FROM shared.sedes
        WHERE activa = TRUE AND deleted_at IS NULL
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.schemata AS s
            WHERE s.schema_name = v_schema
        ) THEN
            RAISE NOTICE 'Schema % no existe, omitiendo', v_schema;
            CONTINUE;
        END IF;

        -- Trigger updated_at en grupos (puede faltar si la sede fue creada antes de V7)
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = v_schema AND table_name = 'grupos'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.triggers
            WHERE trigger_schema = v_schema
              AND event_object_table = 'grupos'
              AND trigger_name = 'trg_grupos_updated_at'
        ) THEN
            EXECUTE format(
                'CREATE TRIGGER trg_grupos_updated_at BEFORE UPDATE ON %I.grupos
                 FOR EACH ROW EXECUTE FUNCTION %I.fn_set_updated_at()',
                v_schema, v_schema
            );
            RAISE NOTICE 'Schema %: trigger updated_at en grupos agregado', v_schema;
        END IF;

        -- Tabla miembro_grupos
        EXECUTE format($f$
            CREATE TABLE IF NOT EXISTS %I.miembro_grupos (
                id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                grupo_id      UUID NOT NULL REFERENCES %I.grupos(id)   ON DELETE CASCADE,
                miembro_id    UUID NOT NULL REFERENCES %I.miembros(id) ON DELETE CASCADE,
                rol           VARCHAR(30) NOT NULL DEFAULT 'PARTICIPANTE'
                              CONSTRAINT chk_miembro_grupos_rol
                              CHECK (rol IN ('LIDER','ASISTENTE','PARTICIPANTE')),
                fecha_ingreso DATE NOT NULL DEFAULT CURRENT_DATE,
                created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                CONSTRAINT uq_miembro_grupo UNIQUE (grupo_id, miembro_id)
            )
        $f$, v_schema, v_schema, v_schema);

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%s_mg_grupo
             ON %I.miembro_grupos(grupo_id)',
            replace(v_schema, '-', '_'), v_schema
        );
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%s_mg_miembro
             ON %I.miembro_grupos(miembro_id)',
            replace(v_schema, '-', '_'), v_schema
        );

        RAISE NOTICE 'Schema %: miembro_grupos lista', v_schema;
    END LOOP;
END $$;

-- ── Actualizar fn_crear_schema_sede para nuevas sedes ─────────────────────
CREATE OR REPLACE FUNCTION shared.fn_crear_schema_sede(
    p_schema_name  VARCHAR,
    p_sede_id      UUID
)
RETURNS VOID AS $$
DECLARE
    v_sql TEXT;
BEGIN
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION sgi_admin', p_schema_name);

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

    EXECUTE format(
        'CREATE OR REPLACE FUNCTION %I.fn_set_updated_at()
         RETURNS TRIGGER AS $f$
         BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
         $f$ LANGUAGE plpgsql', p_schema_name);

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
            estado              VARCHAR(20) NOT NULL DEFAULT ''VISITOR'',
            fecha_ingreso       DATE,
            fecha_bautismo      DATE,
            grupo_id            UUID,
            creado_por          UUID REFERENCES shared.usuarios_sistema(id),
            consolidador_id     UUID,
            metadata            JSONB DEFAULT ''{}'',
            created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            deleted_at          TIMESTAMPTZ
        )', p_schema_name, p_sede_id);

    EXECUTE format(
        'ALTER TABLE %I.miembros ADD CONSTRAINT fk_miembros_consolidador
         FOREIGN KEY (consolidador_id) REFERENCES %I.miembros(id) ON DELETE SET NULL',
        p_schema_name, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_miembros_email  ON %I.miembros(email)  WHERE deleted_at IS NULL',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE INDEX idx_%s_miembros_cedula ON %I.miembros(cedula) WHERE deleted_at IS NULL',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE INDEX idx_%s_miembros_estado ON %I.miembros(estado) WHERE deleted_at IS NULL',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE TRIGGER trg_miembros_updated_at BEFORE UPDATE ON %I.miembros
         FOR EACH ROW EXECUTE FUNCTION %I.fn_set_updated_at()',
        p_schema_name, p_schema_name);

    -- HISTORIAL DE ESTADO
    EXECUTE format('
        CREATE TABLE %I.miembro_estado_historial (
            id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            miembro_id      UUID NOT NULL REFERENCES %I.miembros(id) ON DELETE CASCADE,
            estado_anterior VARCHAR(30) NOT NULL,
            estado_nuevo    VARCHAR(30) NOT NULL,
            motivo          TEXT NOT NULL,
            cambiado_por    UUID NOT NULL REFERENCES shared.usuarios_sistema(id),
            cambiado_en     TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_historial_miembro_fecha
         ON %I.miembro_estado_historial(miembro_id, cambiado_en DESC)',
        replace(p_schema_name, '-', '_'), p_schema_name);

    -- GRUPOS
    EXECUTE format('
        CREATE TABLE %I.grupos (
            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id     UUID NOT NULL DEFAULT %L::UUID,
            nombre      VARCHAR(100) NOT NULL,
            tipo        VARCHAR(30)  NOT NULL DEFAULT ''CELULA''
                        CONSTRAINT chk_grupos_tipo CHECK (tipo IN (''CELULA'',''MINISTERIO'',''CLASE'')),
            lider_id    UUID REFERENCES %I.miembros(id) ON DELETE SET NULL,
            descripcion TEXT,
            activo      BOOLEAN NOT NULL DEFAULT TRUE,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_sede_id, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_grupos_tipo   ON %I.grupos(tipo)  WHERE activo = TRUE',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE TRIGGER trg_grupos_updated_at BEFORE UPDATE ON %I.grupos
         FOR EACH ROW EXECUTE FUNCTION %I.fn_set_updated_at()',
        p_schema_name, p_schema_name);

    -- FK grupos → miembros (grupo_id en miembros, deprecated pero se mantiene en BD)
    EXECUTE format(
        'ALTER TABLE %I.miembros ADD CONSTRAINT fk_miembro_grupo
         FOREIGN KEY (grupo_id) REFERENCES %I.grupos(id)',
        p_schema_name, p_schema_name);

    -- MIEMBRO_GRUPOS (N:M)
    EXECUTE format('
        CREATE TABLE %I.miembro_grupos (
            id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            grupo_id      UUID NOT NULL REFERENCES %I.grupos(id)   ON DELETE CASCADE,
            miembro_id    UUID NOT NULL REFERENCES %I.miembros(id) ON DELETE CASCADE,
            rol           VARCHAR(30) NOT NULL DEFAULT ''PARTICIPANTE''
                          CONSTRAINT chk_mg_rol CHECK (rol IN (''LIDER'',''ASISTENTE'',''PARTICIPANTE'')),
            fecha_ingreso DATE NOT NULL DEFAULT CURRENT_DATE,
            created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            CONSTRAINT uq_miembro_grupo UNIQUE (grupo_id, miembro_id)
        )', p_schema_name, p_schema_name, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_mg_grupo   ON %I.miembro_grupos(grupo_id)',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE INDEX idx_%s_mg_miembro ON %I.miembro_grupos(miembro_id)',
        replace(p_schema_name, '-', '_'), p_schema_name);

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
            tipo        VARCHAR(30)  NOT NULL,
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
            tipo            VARCHAR(20) NOT NULL,
            categoria       VARCHAR(50),
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

    -- AUDIT LOG
    EXECUTE format('
        CREATE TABLE %I.audit_log_sede (
            id          BIGSERIAL PRIMARY KEY,
            usuario_id  UUID REFERENCES shared.usuarios_sistema(id),
            accion      VARCHAR(50)  NOT NULL,
            entidad     VARCHAR(100),
            entidad_id  VARCHAR(100),
            datos_antes JSONB,
            datos_despues JSONB,
            ip_address  VARCHAR(45),
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name);

    INSERT INTO shared.audit_log (accion, entidad, entidad_id, descripcion, exitoso)
    VALUES ('CREATE_SCHEMA', 'SEDE', p_sede_id::TEXT,
            format('Schema %s creado exitosamente', p_schema_name), TRUE);

    RAISE NOTICE 'Schema % creado correctamente para sede %', p_schema_name, p_sede_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
