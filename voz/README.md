# Canal de voz · Fecha de cobro por teléfono

El flujo 03 → 05 de la app («¿Qué día te pagan?» → «¿Qué día te queda mejor?» →
«Listo»), dicho por teléfono, para quien no usa la banca en línea. Una asistente
virtual llama, hace **dos preguntas** (cómo le llega el dinero y qué día), propone
la fecha nueva, dice si esa primera cuota lleva interés y lo deja hecho. Al final,
si la persona no usa la app, le dice dónde queda su agencia más cercana.

La llamada **no recuerda pagar**. Mueve el cobro al día en que la persona ya tiene
el dinero, que es lo que la evidencia del proyecto muestra que funciona (un día de
desfase entre ingreso y cuota sube la mora 10 pp, con asignación aleatoria). Un
recordatorio a clientes sanos, en cambio, midió efecto cero (RCT, n = 8 019).

| Criterio de entrada | Respuesta |
|---|---|
| **(a)** Punto del reloj NCB-022 | Antes del día −15, sobre clientes A1 (0 % de reserva). Es el M1 · Alinear del informe 05 |
| **(b)** Norma | LPC art. 18 lit. n limita el contacto (L–V, 8:00–18:00, nada repetitivo). LPC art. 19-A habilita la asesoría preventiva |
| **(c)** Métrica | Distancia cuota–ingreso, rebote del débito, % de llamadas con fecha cambiada, duración y latencia por turno |

---

## 1. Arquitectura

```
                  TELÉFONO (+503)
                        │  voz en tiempo real
                        ▼
┌──────────────────────── Vapi ────────────────────────┐
│  Deepgram nova-3 (es) → gpt-4o-mini → Azure es-SV   │  ← aquí vive la conversación
│  endpointing, barge-in, relleno «Déjame ver…»        │     n8n NO está en este lazo
└───────────┬──────────────────────────────▲───────────┘
            │ solo 2 tipos de mensaje:      │ { results: [...] }
            │ tool-calls · end-of-call-report
            ▼                               │
┌──────────── n8n (exit0 · local en Docker) ───────────┐
│  Ruta · Voz · Herramientas (Vapi)                    │  traduce Vapi ⇄ /api/voz
│  Ruta · Voz · Llamadas salientes                     │  pide la llamada a Vapi
└───────────┬──────────────────────────────────────────┘
            │ REST · X-Voz-Key
            ▼
┌──────────── Backend Ruta (Spring Boot) ──────────────┐
│  VozController → VozService → FechaCobroService      │  todas las reglas
└───────────┬──────────────────────────────────────────┘
            ▼
      Oracle XE / H2 — CLIENTE, PLAN_FECHA_COBRO, PERFIL_INGRESO, LLAMADA_VOZ, SUCURSAL
```

### ¿La llamada guarda por la API o directo en la base? Por la API.

1. **Las reglas viven en Java.** El +3 días, los días excluidos (28–31), los 14 días
   mínimos entre cobros, el interés, el bloqueo de 6 meses, el aviso de la vista 11 y
   el «+1» del récord están en `FechaCobroService`. Escribir directo en
   `PLAN_FECHA_COBRO` los saltaría, y la app mostraría una fecha que ella misma
   habría rechazado.
2. **Una sola verdad.** App y llamada usan el mismo servicio. Lo que cambia por
   teléfono aparece al instante en el inicio de la app y en sus avisos.
3. **Seguridad.** n8n Cloud no alcanza un Oracle local, y exponer el puerto de la
   base a internet no es opción. La API expone cinco rutas con llave.
4. **El modelo no decide.** El cliente sale de los metadatos de la llamada que
   programó el banco, no de lo que se dice. `fecha_cambiada` lo marca el servidor al
   confirmar, no el modelo.

---

## 2. La llamada

El guion completo está en [`prompt-sistema.md`](prompt-sistema.md). En corto:

1. «Hola, buenos días. ¿Hablo con Edgar?» — si es otra persona, no se menciona el préstamo.
2. Quién llama, que queda grabada, para qué, y si tiene un minuto.
3. **Pregunta 1:** ¿cómo te llega tu dinero? (salario, pensión, remesa, negocio).
4. **Pregunta 2:** ¿qué día del mes? (si no es fijo: ¿inicio, mediados o fin de mes?).
5. Propuesta con el texto exacto del backend: día nuevo, desde cuándo e interés de una sola vez.
6. Con un sí claro, se confirma.
7. Solo si no usa la app: ¿app o agencia? → agencia más cercana.
8. Resumen, gracias, colgar.

«¿Tienes otros gastos?» se quedó fuera a propósito: ninguna regla lo usa, así que
no pasa el criterio de entrada y cansa a quien contesta.

### Reglas que aplica el servidor

| Regla | Dónde | Valor |
|---|---|---|
| Día nuevo = día de ingreso + `offset_min` de la frecuencia | `CATALOGO_FRECUENCIA` | +3 |
| Ingreso del 25 al 27 → pasa al mes siguiente con el primer día que deja el margen incluso en febrero | `FechaCobroService.calcularPorDias` | 25→1, 26→1, 27→2 |
| Fin de mes (28–31) | ídem | → 3 |
| Nunca del 28 al 31; primer cobro nuevo ≥ 14 días después del actual | `PARAMETRO_APP` | `fecha.dias_excluidos`, `fecha.min_dias_entre_cobros` |
| Interés = saldo × tasa anual × días que se corre la cuota ÷ base | `FechaCobroService.costo` | base `fecha.interes_base_dias` = 365 |
| Si la cuota se adelanta, no hay interés | ídem | — |
| El interés no existe si no se aceptó | `ck_plan_fecha_int` en la base | — |
| No se puede volver a cambiar | `PLAN_FECHA_COBRO.bloqueado_hasta` | `fecha.meses_bloqueo` = 6 |
| Solo L–V 8:00–18:00, y la llamada tiene que caber | `VozService.ventanaAbierta` | LPC art. 18 lit. n |
| Solo A1, al día, a más de 15 días de su cobro | `voz.categorias`, `voz.dias_min_antes_cobro` | — |
| ≤ 3 intentos en 7 días, ≤ 1 por día; «no me llamen» es para siempre | `voz.max_intentos`… | Debajo del 7-en-7 de Reg F |
| Primero quien no usa la app | `pendientes()` | — |

---

## 3. Latencia y calidad de voz (20 % de la rúbrica)

Lo que decide si se siente natural es el lazo **oír → pensar → hablar**, y ese lazo
vive completo dentro de Vapi. n8n y el backend solo entran dos veces por llamada.

| Decisión | Por qué |
|---|---|
| Datos precalculados en `variableValues` (nombre, cuota, agencia, Telebanca) | Los primeros turnos no llaman a ninguna herramienta |
| El backend devuelve `decir` ya redactado | El modelo lee y no calcula. Respuestas cortas, montos exactos y sin inventar |
| `request-start` «Déjame ver…» y `request-response-delayed` a los 2.5 s | Nunca hay silencio muerto mientras se consulta |
| `guardar_formulario` es asíncrona | Guardar no hace esperar a la persona |
| Endpointing: `waitSeconds` 0.6, sin puntuación 1.5 s, con número 1.0 s | Las personas mayores pausan más y dictan fechas por partes: no se les corta |
| `stopSpeakingPlan`: 2 palabras y frases de acuse («ajá», «sí», «ya») | Un «ajá» no interrumpe; un «no, espera» sí |
| Voz Azure `es-SV-LorenaNeural` | Acento salvadoreño y dicción clara |
| `gpt-4o-mini`, temperatura 0.2, 250 tokens | Tiempo al primer token bajo y guion estable |
| Telebanca «2 2 1 0, 0 0 0 0» y montos «16 dólares con 56 centavos» | La voz no lee «punto» ni «guion» |

Para afinar en el dashboard de Vapi, con llamadas reales: comparar el
endpointing `vapi` contra el de transcripción, ElevenLabs Flash contra Azure, y un
modelo de Groq contra `gpt-4o-mini`, midiendo la latencia por turno que reporta Vapi.

**Medido en local** (13 sep 2026, 20 llamadas a `proponer_fecha` simulando a Vapi): n8n
más backend, p50 55 ms y p95 64 ms; el backend solo, p50 4 ms. En la nube se suma la
ida y vuelta Vapi → n8n Cloud → túnel, que hay que medir con llamadas reales.

**Si el salto por n8n resulta lento,** se puede apuntar el `server.url` de las dos
herramientas síncronas directo a un adaptador en el backend. n8n quedaría solo para
llamadas salientes y fin de llamada.

---

## 4. Ejecuciones de n8n (trial de 14 días)

- `serverMessages` = `tool-calls` y `end-of-call-report`. Sin esto, Vapi manda
  transcripciones parciales y estados a cada rato, y **cada uno es una ejecución**.
- Por llamada contestada: `proponer_fecha` + `confirmar_fecha` + `guardar_formulario`
  + fin = **≤ 4 ejecuciones**. Sin contestar: **1**. Más **1** por tanda saliente.
- El disparador programado viene **apagado**: la demo usa el webhook manual.
- `saveDataSuccessExecution: none`. n8n no guarda transcripciones ni datos de clientes
  de las ejecuciones que salen bien (LPDP). Los errores sí se guardan, para depurar.
- **Todo se construye y se prueba en el n8n de Docker**, que no tiene límite. A la
  nube se sube solo la versión final.

---

## 5. Contrato `/api/voz` (cabecera `X-Voz-Key`)

| Método y ruta | Quién | Qué hace |
|---|---|---|
| `GET /voz/llamadas/pendientes?limite=` | n8n, tanda | A quién se puede llamar ahora y si la ventana legal está abierta |
| `POST /voz/llamadas[?demo=true]` `{clienteId}` | n8n, antes de marcar | Revalida, registra el intento y devuelve teléfono y `variableValues` |
| `POST /voz/fecha/propuesta` | herramienta `proponer_fecha` | Opciones con días extra, interés y `decir`. No cambia nada |
| `POST /voz/fecha/confirmar` | herramienta `confirmar_fecha` | Cambia la fecha (misma regla que la app), bloquea 6 meses y emite el aviso |
| `POST /voz/llamadas/resultado` | `guardar_formulario` y fin de llamada | Perfil de ingreso, canal de pago y resultado. No borra datos entre los dos envíos |
| `GET /admin/voz/llamadas` | tablero, demo | Últimas 20 llamadas con su perfil |
| `POST /admin/demo/telefono` `{usuario, telefono}` | demo | Pone el teléfono de quien prueba. El dataset no trae números reales |

---

## 6. Cómo correrlo

### Local, sin gastar nada

```bash
docker compose -f infra/n8n/docker-compose.yml up -d
```

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -DskipTests package && java -jar target/ruta-api-0.2.0.jar
```

`backend/.env` necesita `VOZ_KEY` (`openssl rand -hex 24`) y, **solo para la demo de
fin de semana**, `VOZ_DEMO=true`. Maven tiene que correr con JDK 21 o 17: con JDK 26,
Lombok no compila.

Importar los flujos al n8n local:

```bash
docker cp voz/n8n/ruta-voz-herramientas.json ruta-n8n:/tmp/ && docker exec ruta-n8n n8n import:workflow --input=/tmp/ruta-voz-herramientas.json
```

Después, lo mismo con `ruta-voz-llamadas-salientes.json`, `n8n publish:workflow --id=…` y
`docker restart ruta-n8n`. Las credenciales se crean en la UI de n8n con estos
nombres: `Ruta API (X-Voz-Key)`, `Vapi → n8n (X-Vapi-Secret)` y
`Vapi API (Authorization)`.

### En la nube (exit0 + Vapi + número)

1. **Túnel al backend.** `cloudflared tunnel --url http://localhost:8080`. En el nodo
   `Config` de los dos flujos, `api` = `https://<túnel>/api`.
2. **n8n Cloud.** Importar los dos JSON y crear las tres credenciales. Publicar.
3. **Vapi.** Crear una credencial Bearer Token con cabecera `X-Vapi-Secret`, sin
   prefijo, con el mismo valor de la credencial de n8n. Importar un número de
   **Twilio, Vonage o Telnyx**: los números gratuitos de Vapi no hacen llamadas
   salientes ni internacionales. Luego:
   `VAPI_API_KEY=… N8N_WEBHOOK_URL=https://exit0.app.n8n.cloud/webhook/ruta-voz VAPI_CREDENTIAL_ID=… voz/vapi/crear-asistente.sh`
4. En `Config` del flujo saliente, pegar `vapiAssistantId` y `vapiPhoneNumberId`.
5. Demo: `POST /api/admin/demo/telefono` con tu número y luego
   `POST https://exit0.app.n8n.cloud/webhook/ruta-voz-llamar` `{"clienteId":"cust-edgar","demo":true}` con `X-Voz-Key`.

---

## 7. Probar con voz real

La guía paso a paso, desde la llamada emulada sin cuentas hasta el teléfono, está en
[`PROBAR.md`](PROBAR.md).

### Sin número: el simulador de llamada

`http://localhost:8080/api/simulador-llamada.html` hace una llamada web: micrófono y
parlante en vez de un teléfono. Usa el mismo asistente de Vapi, las mismas
herramientas en n8n y las mismas reglas del backend, y al colgar muestra lo que quedó
guardado. Necesita `VOZ_DEMO=true`, la llave **pública** de Vapi, el ID del asistente,
el flujo de herramientas publicado en n8n y el backend alcanzable por el túnel. Solo
cuesta los minutos de Vapi.

### Con número

Vapi no hace llamadas salientes con sus números gratuitos. Hay que importar uno de
Twilio, Vonage o Telnyx. Precios de Twilio al 13 sep 2026:

| Concepto | Precio |
|---|---|
| Llamada saliente a celular de El Salvador | $0.29 / min |
| Llamada saliente a fijo de El Salvador | $0.275 / min |
| Número local de El Salvador (+503) | $9.00 / mes |
| Plataforma Vapi | desde $0.05 / min, más voz, modelo y transcripción |

Una llamada de 3 minutos a un celular sale en torno a **$1.30** (Twilio + Vapi). La
cuenta de prueba de Twilio solo llama a números verificados (hasta 5); basta para
probar con el propio celular.

## 8. Abierto y por decidir

- **Figma.** Decidido el 13 sep 2026: el interés se cobra en todos los canales. La app
  ya lo muestra en la 04 (en cada día, antes de «Aceptar y confirmar») y en la 05
  («Interés de este cambio»), y el chat lo pregunta antes de mover la fecha. Falta
  llevar el mismo cambio a las vistas 04 y 05 de Figma con el puente abierto. La
  historia de Figma (hoy día 28 → día 18) adelanta la cuota y no lleva interés; para
  mostrarlo, el ejemplo tiene que ser el día 3 o el 4.
- **Confirmar con el banco:** la base de días (365 o 360), cómo se calcula el interés
  de los días corridos, y las tasas y saldos, que en el dataset son ilustrativos.
- **Agencias.** Solo están las 6 que la web publica en HTML. El mapa completo lo carga
  el sitio con JavaScript.
- **Frecuencia semanal.** La fecha «semanal» del catálogo guarda un día del mes; por
  voz se dice «cuando ya recibiste tu pago de la semana», sin prometer un margen.
- **Tensión con la evidencia.** El proyecto decidió que el chat no inicia contacto.
  La llamada sí lo inicia, y se sostiene porque entrega el cambio estructural (la
  fecha) y no un recordatorio. Medir contra un grupo control sin llamada.
- **Tercero al teléfono y grabación.** La llamada no revela nada del préstamo sin
  confirmar identidad. Falta validar con legal el aviso de grabación y el
  consentimiento de contacto (LPC art. 18 lit. g).
