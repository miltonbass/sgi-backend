#!/bin/bash
# =============================================================================
# update-domain.sh — Actualiza el dominio de la aplicación SGI
#
# Uso: ./update-domain.sh <nuevo-dominio>
# Ejemplo: ./update-domain.sh membresia.jovenescristianos.co
#
# Pasos que ejecuta:
#   1. Obtiene certificado SSL con Certbot (Let's Encrypt)
#   2. Genera nginx.conf a partir del template
#   3. Valida la configuración de Nginx
#   4. Recarga Nginx
# =============================================================================
set -euo pipefail

DOMAIN="${1:-}"
SGI_DIR="/opt/sgi"
NGINX_DIR="${SGI_DIR}/nginx"
TEMPLATE="${SGI_DIR}/sgi-backend/deploy/nginx.conf.template"
NGINX_CONF="${NGINX_DIR}/nginx.conf"
NGINX_CONF_BACKUP="${NGINX_DIR}/nginx.conf.bak"
CERTBOT_EMAIL="${2:-admin@${DOMAIN}}"
LOG_FILE="${SGI_DIR}/logs/domain-update.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

error_exit() {
    log "ERROR: $*"
    # Restaurar backup si existe
    if [ -f "$NGINX_CONF_BACKUP" ]; then
        log "Restaurando configuración anterior..."
        cp "$NGINX_CONF_BACKUP" "$NGINX_CONF"
        docker exec sgi_nginx nginx -s reload 2>/dev/null || true
    fi
    exit 1
}

# ── Validar parámetros ───────────────────────────────────────────────────────
if [ -z "$DOMAIN" ]; then
    echo "Uso: $0 <dominio>"
    echo "Ejemplo: $0 membresia.jovenescristianos.co"
    exit 1
fi

# Validar que el dominio no tenga protocolo
if [[ "$DOMAIN" == http* ]]; then
    DOMAIN=$(echo "$DOMAIN" | sed 's|https\?://||' | sed 's|/.*||')
    log "Dominio limpio: $DOMAIN"
fi

log "=========================================="
log "Iniciando actualización de dominio: $DOMAIN"
log "=========================================="

# ── Paso 1: Obtener certificado SSL ──────────────────────────────────────────
log "[1/4] Obteniendo certificado SSL para $DOMAIN..."

docker run --rm \
    --name sgi_certbot_run \
    -v sgi_certbot-data:/etc/letsencrypt \
    -v "${SGI_DIR}/certbot/www:/var/www/certbot" \
    certbot/certbot certonly \
    --webroot -w /var/www/certbot \
    -d "$DOMAIN" \
    --email "$CERTBOT_EMAIL" \
    --agree-tos \
    --no-eff-email \
    --non-interactive \
    --keep-until-expiring \
    2>&1 | tee -a "$LOG_FILE"

if [ $? -ne 0 ]; then
    error_exit "Certbot falló al obtener certificado para $DOMAIN"
fi
log "Certificado SSL obtenido exitosamente."

# ── Paso 2: Generar nginx.conf desde template ────────────────────────────────
log "[2/4] Generando nginx.conf para $DOMAIN..."

if [ ! -f "$TEMPLATE" ]; then
    error_exit "Template no encontrado: $TEMPLATE"
fi

# Backup de la config actual
if [ -f "$NGINX_CONF" ]; then
    cp "$NGINX_CONF" "$NGINX_CONF_BACKUP"
fi

# Reemplazar {{DOMAIN}} en el template
sed "s|{{DOMAIN}}|${DOMAIN}|g" "$TEMPLATE" > "$NGINX_CONF"
log "nginx.conf generado."

# ── Paso 3: Validar configuración ────────────────────────────────────────────
log "[3/4] Validando configuración de Nginx..."

if ! docker exec sgi_nginx nginx -t 2>&1 | tee -a "$LOG_FILE"; then
    error_exit "Configuración de Nginx inválida"
fi
log "Configuración válida."

# ── Paso 4: Recargar Nginx ───────────────────────────────────────────────────
log "[4/4] Recargando Nginx..."

docker exec sgi_nginx nginx -s reload 2>&1 | tee -a "$LOG_FILE"
log "Nginx recargado exitosamente."

# ── Guardar dominio actual ───────────────────────────────────────────────────
echo "$DOMAIN" > "${SGI_DIR}/domain-config/current-domain"

log "=========================================="
log "Dominio actualizado exitosamente: https://$DOMAIN"
log "=========================================="
