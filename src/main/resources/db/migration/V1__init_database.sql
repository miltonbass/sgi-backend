-- ============================================================
-- SGI - Historia 1.3 - Init Database
-- V1__init_database.sql
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- ─── ROLES ──────────────────────────────────────────────────

-- Rol admin (solo migraciones y DBA)
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'sgi_admin') THEN
    CREATE ROLE sgi_admin WITH LOGIN PASSWORD '${SGI_ADMIN_PASSWORD}' NOINHERIT;
  END IF;
END $$;

-- Rol app (aplicación Spring Boot)
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'sgi_app') THEN
    CREATE ROLE sgi_app WITH LOGIN PASSWORD '${SGI_APP_PASSWORD}' INHERIT;
  END IF;
END $$;

-- Rol lectura (reportes, analytics)
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'sgi_readonly') THEN
    CREATE ROLE sgi_readonly WITH LOGIN PASSWORD '${SGI_READONLY_PASSWORD}' INHERIT;
  END IF;
END $$;

-- Rol backup
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'sgi_backup') THEN
    CREATE ROLE sgi_backup WITH LOGIN PASSWORD '${SGI_BACKUP_PASSWORD}' NOINHERIT REPLICATION;
  END IF;
END $$;

-- ─── SCHEMA COMPARTIDO ──────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS shared AUTHORIZATION sgi_admin;
COMMENT ON SCHEMA shared IS 'Schema global: sedes, usuarios, config del sistema';

-- Permisos schema shared
GRANT USAGE ON SCHEMA shared TO sgi_app, sgi_readonly, sgi_backup;
GRANT ALL PRIVILEGES ON SCHEMA shared TO sgi_admin;

ALTER DEFAULT PRIVILEGES FOR ROLE sgi_admin IN SCHEMA shared
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sgi_app;

ALTER DEFAULT PRIVILEGES FOR ROLE sgi_admin IN SCHEMA shared
  GRANT USAGE, SELECT ON SEQUENCES TO sgi_app;

ALTER DEFAULT PRIVILEGES FOR ROLE sgi_admin IN SCHEMA shared
  GRANT SELECT ON TABLES TO sgi_readonly, sgi_backup;

-- Search path
ALTER ROLE sgi_app     SET search_path TO shared;
ALTER ROLE sgi_readonly SET search_path TO shared;
ALTER ROLE sgi_admin   SET search_path TO shared, public;