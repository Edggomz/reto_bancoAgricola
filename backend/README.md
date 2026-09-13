# BackEnd-bancoAgricola — API REST de «Ruta» + IA

Backend del módulo de **prevención de mora** de Bancoagrícola (hackathon Entropy
Hack). Sirve el contrato que consume la app React Native (`Desktop/final`),
integra la IA del asesor y del formulario (**Plan B**: Gemini Flash primario +
Groq secundario, con fallback y guardrails), corre el motor diario de la ruta
con **push** (Expo / Firebase) y guarda todo en **Oracle** (o H2 en memoria para
correr sin base de datos).

- **Java 17 · Spring Boot 3.3 · JPA** · Oracle XE 21c (`XEPDB1`) o H2
- Base: `http://localhost:8080/api` (context-path `/api`) · CORS abierto (demo)
- **Sin datos de negocio en el código**: clientes, productos, catálogos, copy de
  los avisos y del chat, umbrales y reglas salen de la base (`PARAMETRO_APP`,
  `CATALOGO_*`, `OFERTA*`, …).

---

## 1. Cómo correrlo

### Con Oracle (lo que usa la demo)

```bash
# 1) Esquema + dataset (idempotente: borra y vuelve a crear). Usuario JULIOPALACIOS en XEPDB1.
set NLS_LANG=AMERICAN_AMERICA.AL32UTF8
sqlplus JULIOPALACIOS/<tu-clave>@//localhost:1521/XEPDB1 @src/main/resources/db/oracle-schema.sql
sqlplus JULIOPALACIOS/<tu-clave>@//localhost:1521/XEPDB1 @src/main/resources/db/oracle-seed.sql

# 2) Variables locales en .env (ya existe; plantilla en .env.example)
# 3) Arrancar
mvn spring-boot:run -Dspring-boot.run.profiles=oracle
```

El usuario de base se crea con lo que dio el equipo (`ALTER SESSION SET
CONTAINER = XEPDB1; CREATE USER JULIOPALACIOS IDENTIFIED BY <tu-clave> …`). Si se
quiere un usuario con privilegios mínimos, `db/00-crear-usuario.sql` crea
`RUTA_APP`.

### Sin Oracle (H2 en memoria, mismo dataset)

```bash
mvn spring-boot:run            # perfil por defecto: h2
```

Carga `db/h2/schema-h2.sql` (derivado del esquema Oracle con
`node tools/seed/oracle-to-h2.mjs`) y `db/h2/data-h2.sql`. Todo funciona igual;
las vistas y funciones PL/SQL de Oracle quedan como apoyo de análisis, la lógica
vive en Java y es la misma en ambos.

### Variables de entorno (`.env`, gitignored)

| Variable | Para qué |
|---|---|
| `ORACLE_URL` / `ORACLE_USER` / `ORACLE_PASSWORD` | Perfil `oracle` (`jdbc:oracle:thin:@//localhost:1521/XEPDB1`) |
| `AI_PRIMARY` / `AI_SECONDARY` | `gemini` / `groq` (Plan B). También `openai` |
| `GEMINI_API_KEY` · `GEMINI_MODEL` (`gemini-3.8-flash`) · `GEMINI_MODEL_FAST` (`gemini-3.5-flash-lite`) | Gemini: principal para preguntas libres, rápido para clasificar y sugerir |
| `GROQ_API_KEY` · `GROQ_MODEL` (`openai/gpt-oss-120b`) | Groq: fallback y guard de entrada (`llama-prompt-guard-2-86m`) |
| `AI_GUARD_ENABLED` | Prompt guard (probabilidad de jailbreak, umbral 0.8) |
| `FIREBASE_CREDENTIALS_B64` o `GOOGLE_APPLICATION_CREDENTIALS` | Service account de Firebase para push FCM. Sin esto, FCM queda `SIMULADO` (Expo sí envía) |
| `PUSH_DIAS_AVISO` (5) · `PUSH_CRON` (`0 0 9 * * *`) · `PUSH_ZONA` | Motor diario |
| `ADMIN_KEY` | Si se define, `/admin/*` exige la cabecera `X-Admin-Key` |

Sin llaves de IA el backend responde con el guion determinista (copy en
`PARAMETRO_APP`); la demo corre igual.

---

## 2. Contrato de la app (`final/src/api/servicios.ts`)

Todo bajo `/api`; salvo el ingreso, requiere `Authorization: Bearer <token>`.

| Vista | Método y ruta | Devuelve |
|---|---|---|
| 01 | `POST /auth/ingreso` `{usuario, clave}` | `{token}` |
| 02 · 10 · 10b | `GET /clientes/yo/inicio` | cliente, `cuenta` (o **null** si no tiene cuenta con nosotros), `creditos` (tarjetas, personales, hipotecarios, bancarios), `credito` principal, `ruta` (activa / solo-fecha / null), `record`, `productos` disponibles, `avisosSinLeer` |
| 03 | `POST /fecha-cobro/frecuencia` `{frecuencia}` | 204 — guarda «¿qué día te pagan?» en `CLIENTE.frecuencia_pago` |
| 04 | `GET /fecha-cobro/opciones?frecuencia=` | día actual y grupos de días **que varían por frecuencia** (quincena: 18/19 y 3/4; fin de mes: 3/4; semanal: lunes tras cada viernes; variable: según los abonos del ledger) |
| 05 | `POST /fecha-cobro` `{frecuencia, dia}` | `{dia, desde, operacion}` · 400 si el día no está en el set |
| 06 | `GET /apartado/creditos` | créditos apartables |
| 07 | `GET /apartado/partes?credito=` | partes **permitidas por la frecuencia** (fin de mes → 1; quincena → 2/3/4; semanal → 2/3/4; variable → 2/3) con calendario y montos |
| 08 | `GET /apartado/origen?credito=&partes=` | cuenta origen (null = abrir cuenta) y los pasos «así funciona» |
| 09 | `POST /apartado` `{credito, partes, automatico}` | resumen; el servidor valida las partes contra la regla |
| 08a · 08b | `GET /cuenta-digital/oferta` · `GET /cuenta-digital/contrato` · `POST /cuenta-digital {acepta}` | abre una cuenta propia (única desde la que se aparta) |
| 02 | `POST /productos/{id}/activar` | activa p. ej. `deposito-plazo`; desaparece del inicio |
| 11 | `GET /clientes/yo/avisos` · `POST /clientes/yo/avisos/leidos` | avisos con fecha ISO y destino |
| 12 | `GET /clientes/yo/record` | racha, próximo +1, hitos, explicación, lo que suma |
| push | `POST /dispositivos` `{token, plataforma}` | registra el teléfono |
| 13–15 | `POST /asesor/sesiones {aviso}` · `POST /asesor/sesiones/{id}/mensajes {texto}` · `POST /asesor/sesiones/{id}/fin` | el asesor con IA |
| IA | `GET /ai/sugerencias/frecuencia` · `/fecha?frecuencia=` · `/partes?credito=` · `POST /ai/form/suggest` · `GET /ai/metrics` | sugerencias pre-resaltadas (la persona decide) |
| admin | `POST /admin/ruta/procesar?fecha=AAAA-MM-DD` (alias `/admin/run-push`) · `GET /admin/metrics` · `GET /admin/notificaciones` · `GET /admin/clientes` · `POST /admin/demo/saldo {usuario, saldo}` | motor con fecha simulada, dashboard, bitácora, usuarios de prueba |
| dashboard | `GET /api/dashboard.html` | tablero web mínimo de gestión |

Errores: `{ "message": "…" }` con 400/401/404/409/500.

---

## 3. Reglas de negocio (validadas en el servidor)

- **Fecha de cobro:** solo días en que ya le pagaron (pago +3 y +4; nunca 28-31),
  y el primer cobro nuevo cae al menos 14 días después del actual. No cambia
  monto ni plazo.
- **Partes:** tantas como veces recibe dinero (`CATALOGO_FRECUENCIA.partes_permitidas`).
  Se reparten terminando en el último pago antes del cobro (2 cada 15 días,
  3 cada 10, 4 semanales); los centavos sobrantes van a la primera parte.
- **Apartar no es abonar:** cada parte se congela en la cuenta
  (`CUENTA.balance_apartado`) y el día del cobro se paga completo. Solo desde una
  cuenta de este banco; sin cuenta, se abre (08a/08b).
- **Productos disponibles** desaparecen al activarse: «Cambiar fecha de cobro»
  cuando hay fecha nueva o ruta activa; «Depósito a plazo» al activarlo
  (`PRODUCTO_ACTIVADO`).
- **Avisos:** confirman lo ocurrido y nunca recuerdan pagar. Nunca «moroso».

## 4. Motor diario y push (`service/RutaMotor`, `PushService`)

`@Scheduled` (cron `ruta.push.cron`) y a demanda con
`POST /api/admin/ruta/procesar?fecha=` (simula otro día para la demo):

1. **Corte cercano**: créditos con saldo pendiente en el ciclo y a `PUSH_DIAS_AVISO`
   días del corte → aviso + push (una vez por ciclo y dispositivo).
2. **Partes**: el día de cada parte la congela; si no alcanza el saldo nace el
   contexto de choque y el aviso «Este mes vino distinto, y está bien» (abre el
   asesor con contexto).
3. **Cobro**: el día del cobro paga la cuota con lo apartado, suma +1 al récord y
   avisa «Pagado, y a tiempo».

Cada aviso se guarda en `AVISO` (vista 11) y se envía a los dispositivos del
cliente: token `ExponentPushToken[...]` → Expo Push Service (sin credenciales);
otro token → Firebase (si hay service account); si no, queda `SIMULADO`. Todo
queda en `NOTIFICACION_ENVIADA`.

Demo del choque: `POST /admin/demo/saldo {"usuario":"daniela.cruz","saldo":1}` y
luego `POST /admin/ruta/procesar?fecha=<día de su parte>`.

## 5. IA (`ai/`)

- `LlmRouter`: HTTP directo a Gemini (`x-goog-api-key`) y Groq/OpenAI
  (`/chat/completions`), con fallback primario → secundario y bitácora en
  `IA_LLAMADA`. Métricas en `GET /api/ai/metrics`.
- `Guardrails`: entrada (regex + Llama Prompt Guard 2 en Groq) y salida (sin
  «moroso», sin urgencia ni «paga ya», sin markdown).
- `AdvisorAiService` (chat): clasifica la intención (etiqueta → guard →
  heurística → modelo JSON) y **ejecuta la acción en el mismo turno**: mover la
  fecha, apartar en partes, dejarlo en automático, agendar con la asesora. Escala
  a una persona solo si el cliente lo pide, no puede pagar, rechaza todo (límite
  por arquetipo; `resistente` = 1) o la conversación pasa de 30 turnos. El canal
  al escalar lo decide la tarjeta (platino/black → asesora nombrada con cita;
  resto → Centro de Atención), nunca si se ofrece una salida. Registra
  transcripción, resultado (`acuerdo | negativa | siguiente_paso | escalado`) y
  fecha acordada en `CHAT_SESION` / `CHAT_MENSAJE`.
- `FormAiService`: sugiere la frecuencia (según los abonos reales), el día y las
  partes; el id sugerido debe existir en el set (si no, heurística).

## 6. Datos de prueba

`tools/seed/generate-seed.mjs` genera `db/oracle-seed.sql` y `db/h2/data-h2.sql`
desde una sola definición: **42 clientes** (12 impecables A1/A2, 15 mejorables
B/C, 15 fatales D/E), cuentas, 70 créditos (tarjetas, personales, hipotecarios,
bancarios), planes, apartados, avisos, récord, chats, ledger, dispositivos y
push. Clave de todos: `ruta2026`. Casos útiles:

| Usuario | Caso |
|---|---|
| `edgar.gomez` | caso base de la demo (platino, diligente, sin frecuencia aún) |
| `mauricio.sosa` · `samuel.quijada` | sin cuenta con nosotros (flujo 08a) |
| `carlos.ramirez` · `paola.rivera` | solo fin de mes → 1 parte |
| `kevin.alas` · `gabriela.romero` | resistente: un rechazo escala |
| `lucia.hernandez` | black: escala a su asesora con cita |

`GET /api/admin/clientes` lista todos con perfil, arquetipo y si tienen cuenta.

## 7. Estructura

```
config/     WebConfig (CORS, scheduling), AiProperties, RutaProperties, PersistenceConfig
domain/     entidades JPA (36 tablas)      repository/Repositorios  (Spring Data)
dto/        App.java (contrato de la app), Api.java (form suggest / login alias)
service/    Contexto, CalendarioPagos, FechaCobroService, ApartadoService, CuentaDigitalService,
            ProductoService, InicioService, RecordService, AvisoService, PushService, RutaMotor,
            CitaService, DispositivoService, DashboardService, AuthService, CopyService, Fechas
ai/         LlmRouter, Guardrails, SystemPrompts, AdvisorAiService, FormAiService
web/        AuthController, AppController, AsesorController, AiController, AdminController
resources/  application.yml, db/oracle-schema.sql, db/oracle-seed.sql, db/h2/*, static/dashboard.html
tools/seed/ generate-seed.mjs, oracle-to-h2.mjs
```

## 8. Pendientes y notas

- **Push real**: falta el service account de Firebase (o un `extra.eas.projectId`
  en la app para tokens de Expo). Hasta entonces los envíos quedan `SIMULADO` y
  se ven en la vista 11 y en el dashboard.
- **Voz** y **WhatsApp**: no abordados (la voz queda para después, por pedido
  del equipo). El enganche natural es un webhook que llame al mismo
  `AdvisorAiService`.
- **mora-playground-java**: la carpeta y el .zip recibidos vienen vacíos (solo
  `.idea/workspace.xml`, que revela una app `com.exit0.mora.MoraPlaygroundApp`
  con `GROQ_API_KEY`). No había código que consultar; la IA se integró con el
  criterio documentado en `GUIA-ENTRENAMIENTO-ASESOR-IA.md`.
- Seguridad: `.env` está en `.gitignore`; el Personal Access Token de GitHub que
  se compartió en una sesión anterior debe revocarse.
