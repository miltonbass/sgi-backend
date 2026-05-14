-- ============================================================================
-- V18 :: H6.1 - Sistema de Células (LIDER_CELULA)
--   1. Agrega grupo_padre_id y lugar en tabla grupos (jerarquía + lugar habitual)
--   2. Crea sesiones_grupo   (reuniones de célula con ofrenda y comentarios)
--   3. Crea asistencias_sesion (asistencia por sesión, miembros + visitantes)
--   4. Actualiza fn_crear_schema_sede
--   5. Seed: usuarios lider.celula.bog / lider.celula.med con grupo de prueba
-- ============================================================================

-- ── 1. Alterar grupos en todos los schemas activos ────────────────────────
DO $$
DECLARE
    v_schema TEXT;
BEGIN
    FOR v_schema IN
        SELECT schema_name FROM shared.sedes
        WHERE deleted_at IS NULL
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.schemata
            WHERE schema_name = v_schema
        ) THEN
            RAISE NOTICE 'Schema % no existe, omitiendo', v_schema;
            CONTINUE;
        END IF;

        -- grupo_padre_id (árbol de células)
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = v_schema AND table_name = 'grupos'
              AND column_name = 'grupo_padre_id'
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I.grupos ADD COLUMN grupo_padre_id UUID
                 REFERENCES %I.grupos(id) ON DELETE SET NULL',
                v_schema, v_schema);
        END IF;

        -- lugar habitual del grupo (donde se reúne normalmente)
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = v_schema AND table_name = 'grupos'
              AND column_name = 'lugar'
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I.grupos ADD COLUMN lugar VARCHAR(200)',
                v_schema);
        END IF;

        RAISE NOTICE 'Schema %: grupos ampliado OK', v_schema;
    END LOOP;
END $$;

-- ── 2-3. Crear sesiones_grupo y asistencias_sesion ────────────────────────
DO $$
DECLARE
    v_schema TEXT;
BEGIN
    FOR v_schema IN
        SELECT schema_name FROM shared.sedes
        WHERE deleted_at IS NULL
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.schemata
            WHERE schema_name = v_schema
        ) THEN
            CONTINUE;
        END IF;

        -- sesiones_grupo
        EXECUTE format($f$
            CREATE TABLE IF NOT EXISTS %I.sesiones_grupo (
                id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                grupo_id        UUID NOT NULL REFERENCES %I.grupos(id) ON DELETE CASCADE,
                fecha           DATE NOT NULL,
                lugar           VARCHAR(200),
                tema            VARCHAR(200),
                comentarios     TEXT,
                ofrenda_monto   NUMERIC(10,2),
                creado_por      UUID REFERENCES shared.usuarios_sistema(id),
                created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
        $f$, v_schema, v_schema);

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%s_sesiones_grupo
             ON %I.sesiones_grupo(grupo_id, fecha DESC)',
            replace(v_schema, '-', '_'), v_schema);

        -- trigger updated_at para sesiones_grupo
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.triggers
            WHERE trigger_schema = v_schema
              AND event_object_table = 'sesiones_grupo'
              AND trigger_name = 'trg_sesiones_grupo_updated_at'
        ) THEN
            EXECUTE format(
                'CREATE TRIGGER trg_sesiones_grupo_updated_at
                 BEFORE UPDATE ON %I.sesiones_grupo
                 FOR EACH ROW EXECUTE FUNCTION %I.fn_set_updated_at()',
                v_schema, v_schema);
        END IF;

        -- asistencias_sesion
        EXECUTE format($f$
            CREATE TABLE IF NOT EXISTS %I.asistencias_sesion (
                id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                sesion_id           UUID NOT NULL REFERENCES %I.sesiones_grupo(id) ON DELETE CASCADE,
                miembro_id          UUID REFERENCES %I.miembros(id) ON DELETE CASCADE,
                visitante_nombre    VARCHAR(150),
                visitante_telefono  VARCHAR(20),
                presente            BOOLEAN NOT NULL DEFAULT TRUE,
                created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
        $f$, v_schema, v_schema, v_schema);

        EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS uq_%s_asistencias_sesion_miembro
             ON %I.asistencias_sesion(sesion_id, miembro_id)
             WHERE miembro_id IS NOT NULL',
            replace(v_schema, '-', '_'), v_schema);

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%s_asistencias_sesion
             ON %I.asistencias_sesion(sesion_id)',
            replace(v_schema, '-', '_'), v_schema);

        RAISE NOTICE 'Schema %: sesiones_grupo y asistencias_sesion OK', v_schema;
    END LOOP;
END $$;

-- ── 4. Actualizar fn_crear_schema_sede ────────────────────────────────────
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

    -- GRUPOS (con grupo_padre_id y lugar desde V18)
    EXECUTE format('
        CREATE TABLE %I.grupos (
            id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id         UUID NOT NULL DEFAULT %L::UUID,
            nombre          VARCHAR(100) NOT NULL,
            tipo            VARCHAR(30)  NOT NULL DEFAULT ''CELULA''
                            CONSTRAINT chk_grupos_tipo CHECK (tipo IN (''CELULA'',''MINISTERIO'',''CLASE'')),
            lider_id        UUID REFERENCES %I.miembros(id) ON DELETE SET NULL,
            grupo_padre_id  UUID REFERENCES %I.grupos(id)   ON DELETE SET NULL,
            descripcion     TEXT,
            lugar           VARCHAR(200),
            activo          BOOLEAN NOT NULL DEFAULT TRUE,
            created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_sede_id, p_schema_name, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_grupos_tipo ON %I.grupos(tipo) WHERE activo = TRUE',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE TRIGGER trg_grupos_updated_at BEFORE UPDATE ON %I.grupos
         FOR EACH ROW EXECUTE FUNCTION %I.fn_set_updated_at()',
        p_schema_name, p_schema_name);

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

    -- EVENTOS
    EXECUTE format('
        CREATE TABLE %I.eventos (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id             UUID NOT NULL DEFAULT %L::UUID,
            titulo              VARCHAR(200) NOT NULL,
            tipo                VARCHAR(30)  NOT NULL DEFAULT ''CULTO''
                                CONSTRAINT chk_eventos_tipo CHECK (tipo IN (''CULTO'',''REUNION'',''CONFERENCIA'',''ESPECIAL'')),
            estado              VARCHAR(20)  NOT NULL DEFAULT ''PROGRAMADO''
                                CONSTRAINT chk_eventos_estado CHECK (estado IN (''PROGRAMADO'',''ABIERTO'',''CERRADO'',''CANCELADO'')),
            descripcion         TEXT,
            fecha_inicio        TIMESTAMPTZ NOT NULL,
            fecha_fin           TIMESTAMPTZ,
            lugar               VARCHAR(200),
            capacidad           INTEGER,
            recurrente          BOOLEAN NOT NULL DEFAULT FALSE,
            patron_recurrencia  JSONB,
            creado_por          UUID REFERENCES shared.usuarios_sistema(id),
            activo              BOOLEAN NOT NULL DEFAULT TRUE,
            created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            deleted_at          TIMESTAMPTZ
        )', p_schema_name, p_sede_id);

    EXECUTE format(
        'CREATE INDEX idx_%s_eventos_fecha ON %I.eventos(fecha_inicio) WHERE deleted_at IS NULL',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE INDEX idx_%s_eventos_tipo_estado ON %I.eventos(tipo, estado) WHERE deleted_at IS NULL',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE TRIGGER trg_eventos_updated_at BEFORE UPDATE ON %I.eventos
         FOR EACH ROW EXECUTE FUNCTION %I.fn_set_updated_at()',
        p_schema_name, p_schema_name);

    -- ASISTENCIAS (eventos congregacionales)
    EXECUTE format('
        CREATE TABLE %I.asistencias (
            id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            sede_id             UUID NOT NULL DEFAULT %L::UUID,
            evento_id           UUID NOT NULL REFERENCES %I.eventos(id) ON DELETE CASCADE,
            miembro_id          UUID REFERENCES %I.miembros(id),
            visitante_nombre    VARCHAR(150),
            visitante_telefono  VARCHAR(20),
            presente            BOOLEAN NOT NULL DEFAULT TRUE,
            observacion         TEXT,
            created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_sede_id, p_schema_name, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_asistencias_evento ON %I.asistencias(evento_id)',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE UNIQUE INDEX uq_%s_asistencias_evento_miembro
         ON %I.asistencias(evento_id, miembro_id)
         WHERE miembro_id IS NOT NULL',
        replace(p_schema_name, '-', '_'), p_schema_name);

    -- SESIONES_GRUPO (reuniones de célula)
    EXECUTE format('
        CREATE TABLE %I.sesiones_grupo (
            id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            grupo_id        UUID NOT NULL REFERENCES %I.grupos(id) ON DELETE CASCADE,
            fecha           DATE NOT NULL,
            lugar           VARCHAR(200),
            tema            VARCHAR(200),
            comentarios     TEXT,
            ofrenda_monto   NUMERIC(10,2),
            creado_por      UUID REFERENCES shared.usuarios_sistema(id),
            created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_schema_name);

    EXECUTE format(
        'CREATE INDEX idx_%s_sesiones_grupo ON %I.sesiones_grupo(grupo_id, fecha DESC)',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE TRIGGER trg_sesiones_grupo_updated_at
         BEFORE UPDATE ON %I.sesiones_grupo
         FOR EACH ROW EXECUTE FUNCTION %I.fn_set_updated_at()',
        p_schema_name, p_schema_name);

    -- ASISTENCIAS_SESION (asistencia a reuniones de célula)
    EXECUTE format('
        CREATE TABLE %I.asistencias_sesion (
            id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            sesion_id           UUID NOT NULL REFERENCES %I.sesiones_grupo(id) ON DELETE CASCADE,
            miembro_id          UUID REFERENCES %I.miembros(id) ON DELETE CASCADE,
            visitante_nombre    VARCHAR(150),
            visitante_telefono  VARCHAR(20),
            presente            BOOLEAN NOT NULL DEFAULT TRUE,
            created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )', p_schema_name, p_schema_name, p_schema_name);

    EXECUTE format(
        'CREATE UNIQUE INDEX uq_%s_asistencias_sesion_miembro
         ON %I.asistencias_sesion(sesion_id, miembro_id)
         WHERE miembro_id IS NOT NULL',
        replace(p_schema_name, '-', '_'), p_schema_name);
    EXECUTE format(
        'CREATE INDEX idx_%s_asistencias_sesion ON %I.asistencias_sesion(sesion_id)',
        replace(p_schema_name, '-', '_'), p_schema_name);

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

-- ── 5. Seed: lider.celula.bog para PAI_BOG ───────────────────────────────
DO $$
DECLARE
    v_sede_bog_id   UUID;
    v_sede_med_id   UUID;
    v_lider_bog_id  UUID := 'a1b2c3d4-1111-1111-1111-000000000001';
    v_lider_med_id  UUID := 'b2c3d4e5-2222-2222-2222-000000000001';
    v_miembro_bog   UUID := 'a1b2c3d4-1111-1111-1111-000000000003';
    v_miembro_med   UUID := 'b2c3d4e5-2222-2222-2222-000000000003';
    v_grupo_bog     UUID := 'a1b2c3d4-1111-1111-1111-000000000004';
    v_grupo_med     UUID := 'b2c3d4e5-2222-2222-2222-000000000004';
    -- BCrypt(10) de Admin2024! (mismo que resto de usuarios de prueba)
    v_hash          TEXT := '$2a$10$EImMOtMf1TQ4z8a5tuhxS.sCxfSinLBj.85nQKOQyqLyXjazliJnS';
    v_extra_bog     UUID;
BEGIN
    SELECT id INTO v_sede_bog_id FROM shared.sedes WHERE codigo = 'PAI_BOG';
    SELECT id INTO v_sede_med_id FROM shared.sedes WHERE codigo = 'PAI_MED';

    -- ── PAI_BOG ──────────────────────────────────────────────────────────────

    IF v_sede_bog_id IS NOT NULL THEN
        -- Usuario sistema
        INSERT INTO shared.usuarios_sistema (id, username, email, password_hash, nombre, apellido, activo)
        VALUES (v_lider_bog_id, 'lider.celula.bog', 'lider.celula.bog@iglesiapaibog.com',
                v_hash, 'Juan', 'Lider-Bog', true)
        ON CONFLICT (email) DO UPDATE
            SET password_hash = v_hash, activo = true;

        -- Asignación a sede
        INSERT INTO shared.usuarios_sedes (id, usuario_id, sede_id, roles, activo)
        VALUES (gen_random_uuid(), v_lider_bog_id, v_sede_bog_id, ARRAY['LIDER_CELULA'], true)
        ON CONFLICT (usuario_id, sede_id) DO UPDATE
            SET roles = ARRAY['LIDER_CELULA'], activo = true;

        -- Miembro en schema sede
        INSERT INTO sede_pai_bog.miembros
            (id, sede_id, usuario_id, nombres, apellidos, email, estado, created_at, updated_at)
        VALUES (v_miembro_bog, v_sede_bog_id, v_lider_bog_id,
                'Juan', 'Lider Bog', 'lider.celula.bog@iglesiapaibog.com',
                'MIEMBRO', NOW(), NOW())
        ON CONFLICT (id) DO NOTHING;

        -- Grupo célula
        INSERT INTO sede_pai_bog.grupos
            (id, sede_id, nombre, tipo, lider_id, descripcion, lugar, activo, created_at, updated_at)
        VALUES (v_grupo_bog, v_sede_bog_id, 'Célula Norte', 'CELULA', v_miembro_bog,
                'Célula de prueba para lider bog', 'Calle 50 # 20-10, Bogotá', true, NOW(), NOW())
        ON CONFLICT (id) DO NOTHING;

        -- Asignar lider al grupo
        INSERT INTO sede_pai_bog.miembro_grupos
            (id, grupo_id, miembro_id, rol, fecha_ingreso, created_at)
        VALUES (gen_random_uuid(), v_grupo_bog, v_miembro_bog, 'LIDER', CURRENT_DATE, NOW())
        ON CONFLICT (grupo_id, miembro_id) DO UPDATE SET rol = 'LIDER';

        -- Agregar 3 miembros existentes como PARTICIPANTE (si existen)
        FOR v_extra_bog IN
            SELECT id FROM sede_pai_bog.miembros
            WHERE deleted_at IS NULL AND id <> v_miembro_bog
            LIMIT 3
        LOOP
            INSERT INTO sede_pai_bog.miembro_grupos
                (id, grupo_id, miembro_id, rol, fecha_ingreso, created_at)
            VALUES (gen_random_uuid(), v_grupo_bog, v_extra_bog, 'PARTICIPANTE', CURRENT_DATE, NOW())
            ON CONFLICT (grupo_id, miembro_id) DO NOTHING;
        END LOOP;

        RAISE NOTICE 'Seed PAI_BOG: lider.celula.bog creado con celula %', v_grupo_bog;
    END IF;

    -- ── PAI_MED ──────────────────────────────────────────────────────────────

    IF v_sede_med_id IS NOT NULL THEN
        INSERT INTO shared.usuarios_sistema (id, username, email, password_hash, nombre, apellido, activo)
        VALUES (v_lider_med_id, 'lider.celula.med', 'lider.celula.med@iglesiapaibog.com',
                v_hash, 'Maria', 'Lider-Med', true)
        ON CONFLICT (email) DO UPDATE
            SET password_hash = v_hash, activo = true;

        INSERT INTO shared.usuarios_sedes (id, usuario_id, sede_id, roles, activo)
        VALUES (gen_random_uuid(), v_lider_med_id, v_sede_med_id, ARRAY['LIDER_CELULA'], true)
        ON CONFLICT (usuario_id, sede_id) DO UPDATE
            SET roles = ARRAY['LIDER_CELULA'], activo = true;

        INSERT INTO sede_pai_med.miembros
            (id, sede_id, usuario_id, nombres, apellidos, email, estado, created_at, updated_at)
        VALUES (v_miembro_med, v_sede_med_id, v_lider_med_id,
                'Maria', 'Lider Med', 'lider.celula.med@iglesiapaibog.com',
                'MIEMBRO', NOW(), NOW())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO sede_pai_med.grupos
            (id, sede_id, nombre, tipo, lider_id, descripcion, lugar, activo, created_at, updated_at)
        VALUES (v_grupo_med, v_sede_med_id, 'Célula Medellin', 'CELULA', v_miembro_med,
                'Célula de prueba para lider med', 'Carrera 80 # 30-20, Medellín', true, NOW(), NOW())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO sede_pai_med.miembro_grupos
            (id, grupo_id, miembro_id, rol, fecha_ingreso, created_at)
        VALUES (gen_random_uuid(), v_grupo_med, v_miembro_med, 'LIDER', CURRENT_DATE, NOW())
        ON CONFLICT (grupo_id, miembro_id) DO UPDATE SET rol = 'LIDER';

        RAISE NOTICE 'Seed PAI_MED: lider.celula.med creado con celula %', v_grupo_med;
    END IF;
END $$;
