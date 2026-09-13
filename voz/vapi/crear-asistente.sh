#!/usr/bin/env bash
# Crea o actualiza el asistente de voz en Vapi con el prompt de voz/prompt-sistema.md.
#
# Se corre desde tu terminal con estas variables (no se guardan en el repo):
#   VAPI_API_KEY         llave privada de Vapi (Dashboard → API Keys)
#   N8N_WEBHOOK_URL      URL de producción del webhook «Vapi» del flujo «Ruta · Voz · Herramientas (Vapi)»
#   VAPI_CREDENTIAL_ID   credencial Bearer Token de Vapi con cabecera X-Vapi-Secret y sin prefijo Bearer
#   VAPI_ASSISTANT_ID    opcional: si viene, actualiza ese asistente en vez de crear otro
#
# Necesita jq y curl.
set -euo pipefail
cd "$(dirname "$0")"

: "${VAPI_API_KEY:?Falta VAPI_API_KEY}"
: "${N8N_WEBHOOK_URL:?Falta N8N_WEBHOOK_URL}"
: "${VAPI_CREDENTIAL_ID:?Falta VAPI_CREDENTIAL_ID}"

cuerpo=$(jq --rawfile prompt ../prompt-sistema.md --arg url "$N8N_WEBHOOK_URL" --arg cred "$VAPI_CREDENTIAL_ID" '
  .model.messages[0].content = $prompt
  | .server.url = $url
  | .server.credentialId = $cred
  | .model.tools |= map(if .type == "function" then .server.url = $url | .server.credentialId = $cred else . end)
' asistente.json)

if [ -n "${VAPI_ASSISTANT_ID:-}" ]; then
  curl -sS -X PATCH "https://api.vapi.ai/assistant/$VAPI_ASSISTANT_ID" \
    -H "Authorization: Bearer $VAPI_API_KEY" -H "Content-Type: application/json" \
    -d "$cuerpo" | jq '{id, name, updatedAt, message}'
else
  curl -sS -X POST "https://api.vapi.ai/assistant" \
    -H "Authorization: Bearer $VAPI_API_KEY" -H "Content-Type: application/json" \
    -d "$cuerpo" | jq '{id, name, createdAt, message}'
fi
