-- =============================================================================
-- V6__alter_refresh_tokens.sql
-- Agrega columnas requeridas por la entidad RefreshToken (Historia 1.4)
-- que no existían en la definición original de shared.refresh_tokens
-- =============================================================================

ALTER TABLE shared.refresh_tokens
    ADD COLUMN IF NOT EXISTS creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS dispositivo  VARCHAR(255);