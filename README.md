# SGI Backend

API REST del **Sistema de Gestión Integral (SGI)** de la Iglesia PAI: membresía, grupos y células, eventos y asistencia, consolidación, alertas de ausencia y reportes, para múltiples sedes.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security (JWT, stateless) · PostgreSQL 15 · Flyway · JdbcTemplate/JPA · Testcontainers

---

## Arquitectura

### Multi-tenant por schema
Cada sede tiene su propio schema en PostgreSQL (`sede_pai_bog`, `sede_pai_med`, …). Los datos comunes (usuarios, sedes, configuración, auditoría) viven en el schema `shared`.

```
request ──► JwtAuthFilter ──► TenantFilter ──► Controller ──► Service ──► Postgres
            valida el JWT     fija el schema    @PreAuthorize          (search_path = sede_xxx, shared)
            (claim sedeSchema)  en TenantContext
```

- El schema de la sede sale **únicamente** del claim `sedeSchema` del JWT firmado, nunca de headers del cliente.
- Las sedes nuevas se crean con `shared.fn_crear_schema_sede(schema, sede_id)`, que crea todas las tablas.

### Autorización
Cada endpoint declara sus roles con `@PreAuthorize`. Roles: `ADMIN_GLOBAL` (hereda `ADMIN_SEDE`), `ADMIN_SEDE`, `PASTOR_PRINCIPAL`, `PASTOR_SEDE`, `SECRETARIA`, `REGISTRO_SEDE`, `CONSOLIDACION_SEDE`, `LIDER_CELULA`.

Un test (`EndpointSecurityIntegrationTest`) recorre todos los endpoints y falla si alguno queda sin protección.

### Módulos (`com.miltonbass.sgi_backend`)
| Paquete | Responsabilidad |
|---|---|
| `auth` | Login, refresh/logout, JWT, bloqueo por intentos fallidos |
| `usuarios`, `sedes` | Usuarios del sistema y sedes |
| `miembros` | Membresía, importación/exportación Excel y PDF |
| `grupos`, `celulas` | Grupos y sesiones de célula |
| `eventos`, `asistencias`, `alertas` | Eventos, asistencia y alertas de ausencia |
| `consolidacion` | Tareas y contactos de consolidación |
| `reportes` | Dashboards y reportes |
| `configuracion` | Branding, SMTP, plantillas, dominio/CORS, seguridad |
| `auditoria` | Auditoría general (AOP) |
| `config`, `security`, `infrastructure`, `exception` | Transversales |

---

## Desarrollo local

### Requisitos
- JDK 21
- Docker (para PostgreSQL y para los tests)

### 1. Levantar PostgreSQL
```bash
docker compose -f src/main/resources/docker-compose.dev.yml up -d postgres
```

### 2. Configuración local
Crea `src/main/resources/application-dev.properties` (está en `.gitignore`):
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/sgi_system
spring.datasource.username=sgi_admin
spring.datasource.password=dev_password_123
spring.datasource.hikari.maximum-pool-size=5
spring.jpa.hibernate.ddl-auto=validate
logging.level.com.miltonbass.sgi_backend=DEBUG
```

### 3. Ejecutar
```bash
./mvnw spring-boot:run
```
La API queda en `http://localhost:8080`. Flyway aplica las migraciones al arrancar.

Health check: `GET /actuator/health`

> Usuarios de prueba del seed: `admin@iglesiapaibog.com` / `secretaria@iglesiapaibog.com` (contraseña en `V5__datos_prueba_auth.sql`). Solo para desarrollo.

---

## Tests

```bash
./mvnw verify
```

- Los tests de integración levantan un PostgreSQL efímero con **Testcontainers** (Docker debe estar corriendo); no tocan tu base local.
- Reporte de cobertura: `target/site/jacoco/index.html`.

| Suite | Qué verifica |
|---|---|
| `EndpointSecurityIntegrationTest` | Todos los endpoints: 401 sin token o con firma alterada, 403 con rol sin permisos, `@PreAuthorize` obligatorio |
| `TenantIsolationIntegrationTest` | Una sede nunca ve datos de otra |
| `AuthIntegrationTest` | Login, refresh, logout, bloqueo de cuenta |
| `SqlInjectionRegressionTest` | Filtros parametrizados |
| `ErrorHandlingIntegrationTest` | Errores 4xx uniformes, nunca 500 por culpa del cliente |
| `JwtServiceTest`, `ProductionSecretsValidatorTest` | Unitarios |

Para agregar tests de integración, extiende `IntegrationTestBase`: ya trae MockMvc, dos sedes con datos y un helper `token(sede, schema, roles...)`.

---

## Migraciones (Flyway)

- Ubicación: `src/main/resources/db/migration`, con el nombre `V{n}__descripcion.sql`.
- **Nunca edites una migración ya aplicada**: crea una nueva.
- Un cambio de tablas de sede debe (1) alterar los schemas existentes y (2) actualizar `fn_crear_schema_sede` para las sedes futuras.

---

## Despliegue

GitHub Actions (`.github/workflows/deploy.yml`):
1. **En cada PR y push a `main`:** compila y corre todos los tests.
2. **Solo en push a `main` con tests en verde:** despliega por SSH ejecutando `/opt/sgi/deploy.sh` en el servidor.

### Variables de entorno en producción
| Variable | Obligatoria | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sí | `prod` |
| `DB_APP_USER`, `DB_APP_PASSWORD` | sí | Credenciales de PostgreSQL |
| `SGI_JWT_SECRET` | sí | Firma de los JWT. Mínimo 32 caracteres |
| `SGI_ENCRYPTION_KEY` | sí | Cifra la contraseña SMTP en BD. Mínimo 32 caracteres |
| `SGI_APP_URL` | no | URL del frontend (links de correo) |
| `SGI_UPLOAD_PATH` | no | Carpeta de logos subidos (por defecto `/opt/sgi/uploads`) |

Con el perfil `prod`, la aplicación **no arranca** si `SGI_JWT_SECRET` o `SGI_ENCRYPTION_KEY` faltan, son cortas o usan el valor por defecto del repositorio.

Generar un secreto: `openssl rand -base64 48`

Nginx: `deploy/nginx.conf.template` y `deploy/update-domain.sh`.
