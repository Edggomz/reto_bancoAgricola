# Cómo probar el canal de voz

Hay tres niveles. El primero no necesita cuentas ni número, y alcanza para probar
todo el recorrido: la llamada, n8n, el backend y la app.

| Nivel | Qué prueba | Qué necesita |
|---|---|---|
| **1 · Llamada emulada** | Guion, n8n, reglas, datos guardados y que la app lo muestre | Nada: Docker, backend y emulador |
| **2 · Llamada web con Vapi** | Además, la voz real, la transcripción y la latencia | Cuenta de Vapi, túnel y n8n Cloud publicado |
| **3 · Llamada por teléfono** | Además, la línea telefónica | Lo anterior y un número de Twilio, Vonage o Telnyx |

---

## Nivel 1 · Llamada emulada (sin cuentas)

El navegador hace de teléfono y de Vapi. Suena, contestas, la asistente dice el guion
en voz alta, y tú respondes con botones, escribiendo o por micrófono (en Chrome).
Cada paso le manda a n8n el mismo mensaje que mandaría Vapi; n8n llama al backend, y
lo que se acuerda aparece en la app.

### Levantar todo

En macOS, un comando levanta n8n, el backend, el emulador y la app:

```bash
./scripts/levantar-demo.sh
```

A mano, o en Windows: `docker compose -f infra/n8n/docker-compose.yml up -d`, luego el
backend con JDK 21 (`mvn -q -DskipTests package` y `java -jar target/ruta-api-0.2.0.jar`
dentro de `backend/`), y la app con `adb reverse tcp:8080 tcp:8080` y
`npx expo start --android` dentro de `frontend/`.

`backend/.env` necesita `VOZ_KEY`, `VOZ_DEMO=true`, `VOZ_N8N_WEBHOOK` y
`VOZ_N8N_SECRETO` (el script las crea). La base es H2 en memoria: al reiniciar el
backend vuelve al dataset original.

### Recorrido de 5 minutos

1. **App:** entra como `edgar.gomez` con la clave `ruta2026`. En el inicio, la cuota
   «se cobra el 28».
2. **Llamada:** abre <http://localhost:8080/api/llamada-emulada.html>, elige a Edgar y
   toca **Llamar al cliente**. El teléfono suena; toca **Contestar**. Con «Cuándo
   suena: en 5 segundos» te da tiempo de cambiar de pantalla.
3. Responde con los botones, escribiendo o por micrófono: «Sí, soy yo» → «Sí, dime» →
   «Pensión» → «El 25» → «Sí, cámbiala». La asistente propone el día 1 y dice cuánto
   interés lleva esa primera cuota.
4. **En cualquier momento** pregúntale algo fuera del guion: «¿quién habla?», «¿es una
   estafa?», «¿cuánto es mi cuota?», «¿cobran algo?», «espérame un momento» o «quiero
   hablar con una persona». Responde y vuelve a la pregunta en que iba. Puedes
   interrumpirla mientras habla.
5. **Lo que ve el banco:** la columna derecha muestra en vivo en qué paso va, el
   formulario que se va llenando, el tiempo de respuesta y la auditoría de esa llamada.
   Al colgar, el teléfono muestra lo que quedó guardado.
6. **App:** recarga («r» en la terminal de Expo o *Reload* en el menú). El inicio ya
   muestra el día nuevo, y en **Avisos** está «Tu cobro ahora es el día 1».
7. **Auditoría:** <http://localhost:8080/api/auditoria.html> tiene toda la historia,
   de la app y de la llamada, y la lista «¿funciona?» marca lo que ya se probó.

### Otros casos que conviene probar

| Qué responder | Qué debe pasar |
|---|---|
| «No, habla su hija» → «¿cuánto debe?» | No menciona el préstamo («Eso solo lo puedo hablar con…»), pregunta a qué hora encontrarlo y queda `tercero` |
| «Ahora no puedo» → «En la tarde» | Pide una hora, la anota y queda `volver_a_llamar` |
| «No me llamen» | Queda `no_llamar` y el cliente sale de las llamadas para siempre |
| «No es fijo» → «A mediados» | Propone según el 15 |
| «El 15 y el 30» | Ofrece dos fechas: la del 18 no lleva interés y la del 3 sí |
| «Cada semana» | Ofrece fechas sin prometer un margen fijo |
| Tocar **Rechazar** | Queda `no_contesto` |
| Llamar otra vez a quien ya cambió | La propuesta dice que se puede cambiar dentro de 6 meses |
| Samuel Quijada (no usa la app, fecha libre) | Cambia la fecha y luego pregunta si paga por app o en agencia, y le dice la agencia más cercana |
| Mariana López o Andrés Molina (no usan la app, ya cambiaron su fecha) | Dice desde cuándo se puede volver a cambiar y queda `bloqueado` |

---

## Nivel 2 · Llamada web con Vapi (voz real, sin número)

1. Cuenta de Vapi: llave **pública** y llave **privada**.
2. Túnel público al backend: `cloudflared tunnel --url http://localhost:8080`.
   Antes, pon `ADMIN_KEY` en `backend/.env`, para que las rutas de administración no
   queden abiertas.
3. n8n Cloud (exit0), flujo **Ruta · Voz · Herramientas (Vapi)**:
   - En `Config`, `api` = `https://<túnel>/api`.
   - Crea las credenciales «Vapi → n8n (X-Vapi-Secret)» y «Ruta API (X-Voz-Key)».
   - Publica el flujo.
4. Vapi:
   - Crea una credencial Bearer Token con cabecera `X-Vapi-Secret`, sin prefijo, y el
     mismo secreto que la credencial de n8n.
   - Corre `voz/vapi/crear-asistente.sh` con tu llave privada.
5. Abre <http://localhost:8080/api/simulador-llamada.html>, pega la llave pública y el
   ID del asistente, y llama.

## Nivel 3 · Llamada por teléfono

Importa en Vapi un número de Twilio, Vonage o Telnyx (*Phone Numbers → Import*). En el
flujo **Ruta · Voz · Llamadas salientes**, pega `vapiAssistantId` y
`vapiPhoneNumberId` en `Config`. Pon tu número al cliente de prueba con
`POST /api/admin/demo/telefono` y dispara
`POST /webhook/ruta-voz-llamar {"clienteId": "cust-edgar", "demo": true}`.
Los costos están en `README.md` §7.
