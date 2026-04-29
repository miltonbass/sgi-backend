-- ============================================================================
-- V9 :: Corrección — asegurar consolidador_id y miembro_estado_historial
--       en todos los schemas de sede (idempotente con IF NOT EXISTS)
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

        -- consolidador_id en miembros
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = v_schema
              AND table_name   = 'miembros'
              AND column_name  = 'consolidador_id'
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I.miembros ADD COLUMN consolidador_id UUID',
                v_schema
            );
            RAISE NOTICE 'Schema %: columna consolidador_id agregada', v_schema;
        END IF;

        -- FK consolidador → miembros (si no existe)
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema     = v_schema
              AND table_name       = 'miembros'
              AND constraint_name  IN ('fk_miembros_consolidador','fk_miembro_consolidador')
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I.miembros ADD CONSTRAINT fk_miembros_consolidador
                 FOREIGN KEY (consolidador_id) REFERENCES %I.miembros(id) ON DELETE SET NULL',
                v_schema, v_schema
            );
            RAISE NOTICE 'Schema %: FK consolidador agregada', v_schema;
        END IF;

        -- Índice consolidador
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%s_miembros_consolidador
             ON %I.miembros(consolidador_id)',
            replace(v_schema, '-', '_'), v_schema
        );

        -- Tabla miembro_estado_historial
        EXECUTE format($f$
            CREATE TABLE IF NOT EXISTS %I.miembro_estado_historial (
                id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                miembro_id      UUID NOT NULL REFERENCES %I.miembros(id) ON DELETE CASCADE,
                estado_anterior VARCHAR(30) NOT NULL,
                estado_nuevo    VARCHAR(30) NOT NULL,
                motivo          TEXT NOT NULL,
                cambiado_por    UUID NOT NULL REFERENCES shared.usuarios_sistema(id),
                cambiado_en     TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
        $f$, v_schema, v_schema);

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_%s_historial_miembro_fecha
             ON %I.miembro_estado_historial(miembro_id, cambiado_en DESC)',
            replace(v_schema, '-', '_'), v_schema
        );

        RAISE NOTICE 'Schema % listo con H2.2', v_schema;
    END LOOP;
END $$;
