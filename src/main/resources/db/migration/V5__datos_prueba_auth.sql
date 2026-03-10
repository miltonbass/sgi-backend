-- =============================================================================
-- V5__datos_prueba_auth.sql
-- Datos de prueba para Historia 1.4: Login JWT Multi-sede
--
-- Usuario admin:       admin@iglesiapaibog.com  / Admin2024!
-- Usuario secretaria:  secretaria@iglesiapaibog.com / Admin2024!
--
-- Hash BCrypt de 'Admin2024!' con strength=12:
-- $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgMRpLOSp1JnL6gHqHxR9K
-- =============================================================================

-- Insertar sede PAI Bogotá
INSERT INTO shared.sedes (id, codigo, nombre, schema_name, ciudad, activa)
VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'PAI_BOG',
    'PAI Bogotá',
    'sede_pai_bog',
    'Bogotá',
    true
)
ON CONFLICT (codigo) DO UPDATE
    SET schema_name = EXCLUDED.schema_name,
        activa      = true;

-- Insertar usuario administrador (campos obligatorios: username, nombre, apellido)
INSERT INTO shared.usuarios_sistema (id, username, email, password_hash, nombre, apellido, activo)
VALUES (
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    'admin.pai',
    'admin@iglesiapaibog.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgMRpLOSp1JnL6gHqHxR9K',
    'Administrador',
    'PAI',
    true
)
ON CONFLICT (email) DO NOTHING;

-- Asignar admin a la sede con roles ADMIN y SECRETARIA
INSERT INTO shared.usuarios_sedes (id, usuario_id, sede_id, roles, activo)
VALUES (
    'c3d4e5f6-a7b8-9012-cdef-123456789012',
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    ARRAY['ADMIN', 'SECRETARIA'],
    true
)
ON CONFLICT (usuario_id, sede_id) DO NOTHING;

-- Insertar usuario secretaria
INSERT INTO shared.usuarios_sistema (id, username, email, password_hash, nombre, apellido, activo)
VALUES (
    'd4e5f6a7-b8c9-0123-defa-234567890123',
    'secretaria.pai',
    'secretaria@iglesiapaibog.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgMRpLOSp1JnL6gHqHxR9K',
    'Secretaria',
    'PAI',
    true
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO shared.usuarios_sedes (id, usuario_id, sede_id, roles, activo)
VALUES (
    'e5f6a7b8-c9d0-1234-efab-345678901234',
    'd4e5f6a7-b8c9-0123-defa-234567890123',
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    ARRAY['SECRETARIA'],
    true
)
ON CONFLICT (usuario_id, sede_id) DO NOTHING;

-- Configuración del sistema
INSERT INTO shared.configuracion_sistema (clave, valor, descripcion)
VALUES
    ('LOGIN_MAX_INTENTOS',    '5',  'Número máximo de intentos fallidos antes de bloquear'),
    ('LOGIN_BLOQUEO_MINUTOS', '15', 'Minutos de bloqueo tras superar intentos fallidos'),
    ('JWT_EXPIRATION_MINUTES','60', 'Duración del access token JWT en minutos')
ON CONFLICT (clave) DO NOTHING;