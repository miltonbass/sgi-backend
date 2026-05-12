-- ============================================================================
-- V17 :: Reparar schema de sede_pai_med
-- ============================================================================
-- sede_pai_med fue creada antes de V7 y estaba inactiva durante V7-V16,
-- por lo que nunca recibió las columnas y tablas añadidas en esas migraciones.
-- Esta migración descarta las tablas incompletas y recrea el schema completo
-- usando fn_crear_schema_sede (ya actualizada por V7-V15).
-- ============================================================================

DO $$
DECLARE
    v_schema  TEXT;
    v_sede_id UUID;
BEGIN
    SELECT schema_name, id
      INTO v_schema, v_sede_id
      FROM shared.sedes
     WHERE codigo = 'PAI_MED'
     LIMIT 1;

    IF v_schema IS NULL THEN
        RAISE NOTICE 'PAI_MED no encontrada — nada que reparar';
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.schemata
         WHERE schema_name = v_schema
    ) THEN
        RAISE NOTICE 'Schema % no existe — se crea desde cero', v_schema;
        PERFORM shared.fn_crear_schema_sede(v_schema, v_sede_id);
        RETURN;
    END IF;

    RAISE NOTICE 'Reparando schema %...', v_schema;

    -- Descartar tablas en orden inverso (CASCADE gestiona FKs residuales)
    EXECUTE format('DROP TABLE IF EXISTS %I.contactos_consolidacion  CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.tareas_consolidacion     CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.tareas_seguimiento       CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.alertas_ausencia         CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.asistencias              CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.eventos                  CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.miembro_grupos           CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.miembro_estado_historial CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.miembros                 CASCADE', v_schema);
    EXECUTE format('DROP TABLE IF EXISTS %I.grupos                   CASCADE', v_schema);

    -- Descartar función local (será recreada por fn_crear_schema_sede)
    EXECUTE format('DROP FUNCTION IF EXISTS %I.fn_set_updated_at() CASCADE', v_schema);

    -- Recrear con la versión actualizada de fn_crear_schema_sede
    PERFORM shared.fn_crear_schema_sede(v_schema, v_sede_id);

    RAISE NOTICE 'Schema % reparado correctamente', v_schema;
END $$;
