-- ============================================================================
-- V17 :: Reparar schema de sede_pai_med
-- ============================================================================
-- sede_pai_med fue creada antes de V7 y estaba inactiva durante V7-V16,
-- por lo que nunca recibio las columnas y tablas anadidas en esas migraciones.
-- Solución: DROP SCHEMA CASCADE para eliminar todos los objetos existentes
-- (incluyendo tablas desconocidas como cuentas_financieras) y recrear desde
-- cero con fn_crear_schema_sede, que ya tiene el schema completo y actualizado.
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
        RAISE NOTICE 'PAI_MED no encontrada - nada que reparar';
        RETURN;
    END IF;

    RAISE NOTICE 'Eliminando schema % completo...', v_schema;

    -- DROP SCHEMA CASCADE elimina todas las tablas, funciones, indices, etc.
    -- sin necesidad de conocer su lista exacta
    EXECUTE format('DROP SCHEMA IF EXISTS %I CASCADE', v_schema);

    RAISE NOTICE 'Recreando schema % con fn_crear_schema_sede...', v_schema;

    -- fn_crear_schema_sede comienza con CREATE SCHEMA IF NOT EXISTS
    -- y crea todas las tablas en su version mas actualizada
    PERFORM shared.fn_crear_schema_sede(v_schema, v_sede_id);

    RAISE NOTICE 'Schema % recreado correctamente', v_schema;
END $$;
