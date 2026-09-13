#!/usr/bin/env bash
# Detiene lo que levantó scripts/levantar-demo.sh. El emulador se queda abierto (ciérralo a mano).
set -uo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$RAIZ/.demo-logs/backend.pid" ]; then
  kill "$(cat "$RAIZ/.demo-logs/backend.pid")" 2>/dev/null && echo "Backend detenido"
  rm -f "$RAIZ/.demo-logs/backend.pid"
fi
pkill -f "ruta-api-0.2.0.jar" 2>/dev/null && echo "Backend detenido"
pkill -f "$RAIZ/frontend/node_modules/.bin/expo" 2>/dev/null && echo "Expo detenido"
docker compose -f "$RAIZ/infra/n8n/docker-compose.yml" stop && echo "n8n detenido"
