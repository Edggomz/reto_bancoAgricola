#!/usr/bin/env bash
# Levanta todo lo de la demo local, sin cuentas: n8n (Docker), backend, emulador Android y la app.
#
#   ./scripts/levantar-demo.sh               todo, y deja la app corriendo en esta terminal
#   ./scripts/levantar-demo.sh --sin-app     solo n8n y backend
#   ./scripts/levantar-demo.sh --reiniciar   reinicia el backend: los datos vuelven al dataset original
#   ./scripts/levantar-demo.sh --reimportar  vuelve a cargar credenciales y flujos en n8n
#
# Necesita Docker Desktop, JDK 21 o 17, y para la app el Android SDK con un AVD.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$RAIZ/.demo-logs"
ENV="$RAIZ/backend/.env"
JAR="$RAIZ/backend/target/ruta-api-0.2.0.jar"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
SIN_APP=false
REIMPORTAR=false
REINICIAR=false
for arg in "$@"; do
  case "$arg" in
    --sin-app) SIN_APP=true ;;
    --reimportar) REIMPORTAR=true ;;
    --reiniciar) REINICIAR=true ;;
    *) echo "Opción desconocida: $arg" >&2; exit 2 ;;
  esac
done
mkdir -p "$LOGS"

paso() { printf '\n\033[1m▸ %s\033[0m\n' "$*"; }
esperar() { # esperar URL segundos
  for _ in $(seq 1 "$2"); do
    codigo=$(curl -s -o /dev/null -w '%{http_code}' "$1" || true)
    case "$codigo" in 200|204|401) return 0 ;; esac
    sleep 1
  done
  echo "No respondió $1" >&2
  return 1
}

# 1. Llaves locales en backend/.env (no se sube al repo)
paso "Llaves locales"
if [ ! -f "$ENV" ]; then
  cp "$RAIZ/backend/.env.example" "$ENV"
  REIMPORTAR=true # llaves nuevas: n8n tiene que recibirlas
fi
poner() {
  if ! grep -q "^$1=." "$ENV"; then
    grep -v "^$1=" "$ENV" > "$ENV.tmp" || true
    echo "$1=$2" >> "$ENV.tmp"
    mv "$ENV.tmp" "$ENV"
    echo "  $1 creada"
    REIMPORTAR=true
  fi
}
poner VOZ_KEY "$(openssl rand -hex 24)"
poner VOZ_DEMO true
poner VOZ_N8N_WEBHOOK http://localhost:5678/webhook/ruta-voz
poner VOZ_N8N_SECRETO "$(openssl rand -hex 16)"
leer() { grep "^$1=" "$ENV" | cut -d= -f2-; }
[ -f "$RAIZ/frontend/.env" ] || cp "$RAIZ/frontend/.env.example" "$RAIZ/frontend/.env"

# 2. n8n en Docker con los dos flujos
paso "n8n (Docker)"
if ! docker info >/dev/null 2>&1; then
  echo "  abriendo Docker Desktop…"
  open -a Docker
  for _ in $(seq 1 90); do docker info >/dev/null 2>&1 && break; sleep 2; done
fi
docker compose -f "$RAIZ/infra/n8n/docker-compose.yml" up -d
esperar http://localhost:5678/healthz 90
# Sin tuberías con grep -q: con pipefail, el corte de grep hace fallar el comando que escribe.
flujos="$(docker exec ruta-n8n n8n list:workflow 2>/dev/null || true)"
if $REIMPORTAR || ! grep -q RutaVozHerram001 <<<"$flujos"; then
  tmp="$(mktemp)"
  cat > "$tmp" <<JSON
[
  {"id": "RutaVapiSecret01", "name": "Vapi → n8n (X-Vapi-Secret)", "type": "httpHeaderAuth", "data": {"name": "X-Vapi-Secret", "value": "$(leer VOZ_N8N_SECRETO)"}},
  {"id": "RutaVozKeyApi001", "name": "Ruta API (X-Voz-Key)", "type": "httpHeaderAuth", "data": {"name": "X-Voz-Key", "value": "$(leer VOZ_KEY)"}},
  {"id": "RutaVapiApiKey01", "name": "Vapi API (Authorization)", "type": "httpHeaderAuth", "data": {"name": "Authorization", "value": "Bearer PEGAR_LLAVE_PRIVADA_DE_VAPI"}}
]
JSON
  docker cp "$tmp" ruta-n8n:/tmp/credenciales.json && rm -f "$tmp"
  docker exec -u root ruta-n8n chown node /tmp/credenciales.json
  docker exec ruta-n8n n8n import:credentials --input=/tmp/credenciales.json
  docker exec -u root ruta-n8n rm -f /tmp/credenciales.json
  for flujo in ruta-voz-herramientas ruta-voz-llamadas-salientes; do
    docker cp "$RAIZ/voz/n8n/$flujo.json" "ruta-n8n:/tmp/$flujo.json"
    docker exec -u root ruta-n8n chown node "/tmp/$flujo.json"
    docker exec ruta-n8n n8n import:workflow --input="/tmp/$flujo.json"
  done
  docker exec ruta-n8n n8n publish:workflow --id=RutaVozHerram001
  docker exec ruta-n8n n8n publish:workflow --id=RutaVozSalient01
  docker restart ruta-n8n >/dev/null
  esperar http://localhost:5678/healthz 90
fi
echo "  arriba en :5678"

# 3. Backend
paso "Backend"
arriba() { [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/admin/auditoria/resumen || true)" != "000" ]; }
if arriba && $REINICIAR; then
  if [ -f "$LOGS/backend.pid" ]; then kill "$(cat "$LOGS/backend.pid")" 2>/dev/null || true; fi
  pkill -f "ruta-api-0.2.0.jar" 2>/dev/null || true
  for _ in $(seq 1 20); do arriba || break; sleep 1; done
fi
if arriba; then
  echo "  ya estaba arriba en :8080 (usa --reiniciar para volver al dataset original)"
else
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)"
  if [ -z "$JAVA_HOME" ]; then echo "Falta un JDK 21 o 17 (con el 26 de Homebrew Lombok no compila)." >&2; exit 1; fi
  export JAVA_HOME
  if [ ! -f "$JAR" ] || [ -n "$(find "$RAIZ/backend/src" "$RAIZ/backend/pom.xml" -newer "$JAR" -print -quit)" ]; then
    echo "  compilando…"
    (cd "$RAIZ/backend" && mvn -q -DskipTests package)
  fi
  (cd "$RAIZ/backend" && nohup "$JAVA_HOME/bin/java" -jar "$JAR" > "$LOGS/backend.log" 2>&1 & echo $! > "$LOGS/backend.pid")
  esperar http://localhost:8080/api/admin/auditoria/resumen 120
  echo "  arriba (registro en .demo-logs/backend.log)"
fi

cat <<TXT

Listo:
  Llamada emulada    http://localhost:8080/api/llamada-emulada.html
  Auditoría          http://localhost:8080/api/auditoria.html
  Dashboard          http://localhost:8080/api/dashboard.html
  n8n local          http://localhost:5678
  Usuarios de prueba edgar.gomez, samuel.quijada, mauricio.sosa… clave ruta2026
TXT

$SIN_APP && exit 0

# 4. Emulador y app
paso "Emulador Android"
if [ ! -x "$ADB" ]; then echo "No encontré el Android SDK en $ANDROID_HOME." >&2; exit 1; fi
if ! grep -q "^emulator-" <<<"$("$ADB" devices)"; then
  AVD="${AVD:-$("$ANDROID_HOME/emulator/emulator" -list-avds | grep -m1 -E 'Pixel_6|S23' || "$ANDROID_HOME/emulator/emulator" -list-avds | head -1)}"
  echo "  arrancando $AVD"
  nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-snapshot-save -no-boot-anim > "$LOGS/emulador.log" 2>&1 &
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
fi
"$ADB" reverse tcp:8080 tcp:8080 >/dev/null
"$ADB" reverse tcp:8081 tcp:8081 >/dev/null
# Expo Go desde la caché local de Expo, si ya se descargó alguna vez
if ! grep -q host.exp.exponent <<<"$("$ADB" shell pm list packages)"; then
  apk="$(ls "$HOME"/.expo/android-apk-cache/Expo-Go-*.apk 2>/dev/null | tail -1 || true)"
  if [ -n "$apk" ]; then echo "  instalando $(basename "$apk")"; "$ADB" install -r "$apk" >/dev/null; fi
fi

paso "App (Expo). Deja esta terminal abierta; «r» recarga la app."
cd "$RAIZ/frontend"
[ -e node_modules ] || npm install
EXPO_PUBLIC_API_URL=http://localhost:8080/api exec npx expo start --android
