-- ============================================================
-- SGI - Historia 1.3 - Seed Data
-- V4__seed_data.sql
-- ============================================================
SET search_path TO shared, public;

-- Configuración del sistema
INSERT INTO shared.configuracion_sistema (clave, valor, descripcion, tipo) VALUES
  ('JWT_EXPIRATION_MINUTES',  '60',     'Expiración access token (min)',         'INTEGER'),
  ('JWT_REFRESH_DAYS',        '30',     'Expiración refresh token (días)',        'INTEGER'),
  ('LOGIN_MAX_INTENTOS',      '5',      'Intentos antes de bloqueo',             'INTEGER'),
  ('LOGIN_BLOQUEO_MINUTOS',   '15',     'Minutos de bloqueo',                    'INTEGER'),
  ('PASSWORD_MIN_LENGTH',     '8',      'Longitud mínima contraseña',            'INTEGER'),
  ('BACKUP_RETENTION_DAYS',   '30',     'Días retención backups',                'INTEGER'),
  ('MULTITENANT_STRATEGY',    'SCHEMA', 'Estrategia: SCHEMA | DATABASE',         'STRING'),
  ('VERSION_SCHEMA',          '1.0.0',  'Versión del schema',                    'STRING'),
  ('SISTEMA_NOMBRE',          'SGI - Sistema de Gestión Integral', 'Nombre del sistema', 'STRING'),
  ('IGLESIA_NOMBRE',          'Iglesia PAI',                       'Nombre de la iglesia', 'STRING')
ON CONFLICT (clave) DO NOTHING;

-- Sede principal - Bogotá
INSERT INTO shared.sedes (codigo, nombre, schema_name, ciudad, departamento, email, zona_horaria)
VALUES ('PAI_BOG', 'Iglesia PAI Bogotá', 'sede_pai_bog', 'Bogotá', 'Cundinamarca', 'bogota@iglesiaipai.org', 'America/Bogota')
ON CONFLICT (codigo) DO NOTHING;

-- Usuario super-administrador inicial
-- Password: Admin@SGI2025! → BCrypt hash
INSERT INTO shared.usuarios_sistema
  (username, email, password_hash, nombre, apellido, super_admin, debe_cambiar_password)
VALUES
  ('superadmin', 'admin@sgi.iglesiaipai.org',
   '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TsuQePyHkI5h9Y5sDSxcD.lGkIxy', -- Admin@SGI2025!
   'Super', 'Administrador', TRUE, TRUE)
ON CONFLICT (username) DO NOTHING;

-- Crear schema para sede Bogotá
DO $$
DECLARE v_sede_id UUID;
BEGIN
  SELECT id INTO v_sede_id FROM shared.sedes WHERE codigo = 'PAI_BOG';
  IF v_sede_id IS NOT NULL THEN
    PERFORM shared.fn_crear_schema_sede('sede_pai_bog', v_sede_id);
  END IF;
END $$;