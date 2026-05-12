-- V16__fix_sede_schemas_missing_columns.sql
-- Agrega columnas faltantes en schemas de sede creados antes de V7.
-- Usa ADD COLUMN IF NOT EXISTS para ser idempotente.

DO $$
DECLARE
    v_schema TEXT;
BEGIN
    FOR v_schema IN
        SELECT schema_name FROM shared.sedes
        WHERE deleted_at IS NULL
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.miembros
             ADD COLUMN IF NOT EXISTS creado_por UUID
             REFERENCES shared.usuarios_sistema(id)',
            v_schema
        );
    END LOOP;
END $$;
