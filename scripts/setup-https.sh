#!/usr/bin/env bash
# Emite (ou renova) certificado Let's Encrypt para emporiomachado.duckdns.org
# Rodar no VPS, na pasta ~/diskcerveja:
#   bash scripts/setup-https.sh
#
# Requisitos:
#   - DuckDNS já apontando para o IP deste VPS
#   - Portas 80 e 443 abertas
#   - Em ~/diskcerveja/.env: LETSENCRYPT_EMAIL=seu@email.com

set -euo pipefail

cd "$(dirname "$0")/.."

DOMAIN="${DOMAIN:-emporiomachado.duckdns.org}"
COMPOSE=(sudo docker compose -f docker-compose.prod.yml --env-file .env)
LIVE_DIR="./certbot/conf/live/${DOMAIN}"
CERTBOT=(sudo docker run --rm
  -v "$(pwd)/certbot/www:/var/www/certbot"
  -v "$(pwd)/certbot/conf:/etc/letsencrypt"
  certbot/certbot)

if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  set -a
  # shellcheck source=/dev/null
  source .env
  set +a
fi

EMAIL="${LETSENCRYPT_EMAIL:-}"
if [[ -z "$EMAIL" ]]; then
  echo "Defina LETSENCRYPT_EMAIL no arquivo .env (ex.: LETSENCRYPT_EMAIL=voce@gmail.com)"
  exit 1
fi

mkdir -p ./certbot/www ./certbot/conf

echo "==> Garantindo certificado temporário para o nginx subir..."
if [[ ! -d "${LIVE_DIR}" ]]; then
  sudo mkdir -p "${LIVE_DIR}"
  sudo openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout "${LIVE_DIR}/privkey.pem" \
    -out "${LIVE_DIR}/fullchain.pem" \
    -subj "/CN=${DOMAIN}"
fi

echo "==> Subindo proxy (nginx)..."
"${COMPOSE[@]}" up -d proxy

echo "==> Aguardando nginx responder na porta 80..."
for i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1/.well-known/acme-challenge/" >/dev/null 2>&1 \
    || curl -fsS -o /dev/null -w "%{http_code}" "http://127.0.0.1/" | grep -qE '301|200|404'; then
    break
  fi
  sleep 1
done

echo "==> Solicitando certificado Let's Encrypt para ${DOMAIN}..."
# Se ainda for o self-signed de bootstrap, remove para o certbot gravar o real.
if [[ -f "${LIVE_DIR}/fullchain.pem" ]] \
  && ! sudo openssl x509 -in "${LIVE_DIR}/fullchain.pem" -noout -issuer 2>/dev/null | grep -qi "Let's Encrypt"; then
  sudo rm -rf "./certbot/conf/live/${DOMAIN}" \
    "./certbot/conf/archive/${DOMAIN}" \
    "./certbot/conf/renewal/${DOMAIN}.conf"
fi

"${CERTBOT[@]}" certonly \
  --webroot \
  --webroot-path=/var/www/certbot \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d "$DOMAIN"

echo "==> Recarregando nginx com o certificado real..."
"${COMPOSE[@]}" up -d --force-recreate proxy

echo
echo "OK — HTTPS ativo em https://${DOMAIN}"
echo "Cardápio: https://${DOMAIN}/p"
echo
echo "Em Ajustes do sistema, defina a URL pública:"
echo "  https://${DOMAIN}"
echo
echo "Renovação (a cada ~60 dias, ou no cron):"
echo "  cd ~/diskcerveja && sudo docker run --rm -v \"\$(pwd)/certbot/www:/var/www/certbot\" -v \"\$(pwd)/certbot/conf:/etc/letsencrypt\" certbot/certbot renew && sudo docker compose -f docker-compose.prod.yml --env-file .env exec proxy nginx -s reload"
