# Ruta · Prevención de mora para Banca Móvil

Módulo de cobranza preventiva de Bancoagrícola para el reto **Banca inteligente**
del Entropy Hack. La idea es simple: no rescatar a quien ya se atrasó, sino
evitar que un cliente sano llegue a atrasarse. Toda la experiencia ocurre
**antes** del vencimiento, en el único tramo donde todavía no hay costo para el
banco ni para la persona.

El sistema hace cinco cosas, y deja constancia de todas:

1. **Mueve la fecha de cobro** al día en que la persona ya tiene el dinero.
2. **Aparta la cuota en partes** que se congelan en su cuenta y se pagan solas.
3. **Avisa** de lo que va pasando, y nunca recuerda pagar.
4. **Conversa** con un asesor de IA que resuelve en el mismo turno y solo escala
   a una persona cuando el caso se sale de sus manos.
5. **Llama** a quien no usa la app: una asistente de voz hace dos preguntas, mueve
   la fecha de cobro y guarda el formulario. Ver [`voz/README.md`](voz/README.md) y, para
   probarlo sin cuentas ni número, [`voz/PROBAR.md`](voz/PROBAR.md).

Cada paso queda en un **registro de auditoría** con tablero propio, para entender
qué pasó y comprobar que funciona.

> **¿Vas a presentar o probar la demo?** Empieza por [`DEMO.md`](DEMO.md): un comando
> levanta todo y ahí está el recorrido de cinco minutos.

---

## 1. Arquitectura

```
┌──────────────── App móvil · React Native (Expo SDK 57) ────────────────┐
│  Vistas → hooks (useDatos) → src/api/servicios.ts → fetch              │
│  Sin datos propios: todo lo que se ve llega del backend                │
└───────────────────────────────┬────────────────────────────────────────┘
                                │  REST · JSON · Bearer
                                ▼
┌────────── Backend · Java 17 + Spring Boot 3.3 (http://host:8080/api) ──────────┐
│  web/ controladores → service/ reglas de negocio → repository/ (JPA)           │
│                                                                                │
│  ai/       LlmRouter (Gemini → Groq con fallback), Guardrails, SystemPrompts,  │
│            AdvisorAiService (chat), FormAiService (sugerencias)                │
│  service/  RutaMotor (@Scheduled): corte cercano, apartar, cobrar              │
│            PushService: Expo Push Service o Firebase Cloud Messaging           │
└───────────────────────────────┬────────────────────────────────────────────────┘
                                ▼
                     Oracle XE 21c (XEPDB1)  ·  o H2 en memoria
                     40 tablas · dataset de 42 clientes
```

**Principio de diseño:** el backend no contiene ni un dato de negocio en el
código. Nombres, montos, fechas, catálogos, umbrales y hasta el texto de cada
aviso y de cada respuesta del chat salen de la base, sobre todo de
`PARAMETRO_APP` y de las tablas `CATALOGO_*`. Cambiar el copy del producto no
requiere recompilar.

## 2. El flujo, vista por vista

| # | Vista | Qué hace |
|---|---|---|
| 01 | Ingreso | Usuario y clave contra `CLIENTE`, token de sesión |
| 02 | Inicio | Saldo primero, luego los productos bancarios, «Mi ruta», récord y los productos disponibles |
| 03 | ¿Qué día te pagan? | Quincena y fin de mes · Solo fin de mes · Cada semana · Es variable |
| 04 | ¿Qué día te queda mejor? | Solo días en que ya le pagaron. Cambian según la respuesta de 03 |
| 05 | Listo | Confirma el día nuevo y propone apartar |
| 06 | ¿Qué cuota apartamos? | Los créditos apartables. Con uno solo, se salta |
| 07 | ¿En cuántas partes? | Las partes que permite su forma de pago, con calendario y montos |
| 08 | ¿Automático? | Cuenta origen y los tres pasos de cómo funciona |
| 08a · 08b | Abre tu cuenta · Revisa y firma | Rama para quien solo tiene crédito y no tiene cuenta con el banco |
| 09 | Tu ruta quedó lista | Resumen de lo acordado |
| 10 · 10b | Inicio con ruta · Inicio solo con fecha | El seguimiento vive en el inicio |
| 11 | Avisos | Todo lo que el sistema notificó, con aspecto de pantalla bloqueada |
| 12 | Mi récord | Meses seguidos, camino a 24 de 24 y qué te está sumando |
| 13–15 | Asesor bancario | El chat con IA, como ventana sobre la vista de origen |

## 3. Reglas de negocio, validadas en el servidor

El cliente nunca decide solo. Cada regla se vuelve a comprobar en el backend.

**Fecha de cobro.** Solo se ofrecen días posteriores a un pago de la persona, con
un margen de 3 o 4 días. Nunca del 28 al 31, para no romper en febrero. Además el
primer cobro con la fecha nueva cae al menos 14 días después del cobro actual,
para que no le lleguen dos cobros seguidos. Cambiar la fecha no toca la cuota ni
el plazo. Si la primera cuota se corre unos días, esos días llevan interés una sola
vez: la vista 04 lo muestra en cada día antes de confirmar, la 05 lo deja escrito, y
el chat y la voz lo preguntan. Sin aceptarlo, el servidor devuelve 400. Un día fuera
del conjunto también devuelve 400, y después de un cambio la fecha queda fija 6 meses.

**Cuántas partes.** Depende de cuántas veces recibe dinero, no de lo que el
cliente escriba:

| Le pagan | Partes permitidas | Sugerida |
|---|---|---|
| Quincena y fin de mes | 2, 3 o 4 | 2 |
| Solo fin de mes | 1 | 1 |
| Cada semana | 2, 3 o 4 | 4 |
| Es variable | 2 o 3 | 2 |

Quien cobra una sola vez al mes no puede repartir en dos: se le aparta la cuota
completa justo después de su pago. Las partes se reparten terminando en el último
pago antes del cobro, y los centavos sobrantes van a la primera.

**Apartar no es abonar.** Cada parte se congela en la cuenta de la persona, en
`CUENTA.balance_apartado`. El dinero sigue siendo suyo y el día del cobro se paga
la cuota completa de una sola vez. Solo se puede apartar desde una cuenta del
propio banco; quien no tiene una, la abre en el mismo flujo.

**Productos disponibles.** Lo que ya se activó deja de ofrecerse. «Cambiar fecha
de cobro» desaparece cuando hay fecha nueva o ruta activa, y el depósito a plazo
cuando se activa. Si no queda ninguno, la sección entera se va del inicio.

## 4. El asesor con IA

El chat **es** el asesor. No agenda para después: ejecuta la acción en el mismo
turno.

- **Clasificación en capas.** Primero las etiquetas exactas de los chips, luego
  el guard de inyección, luego una heurística de expresiones regulares y, al
  final, el modelo, que devuelve JSON con la intención y los datos que trae el
  mensaje. Si el modelo no responde, la heurística sostiene la conversación
  completa.
- **Acciones reales.** Mover la fecha, apartar en partes, dejarlo en automático y
  agendar con la asesora. Son las únicas cuatro. El modelo no puede inventar
  condiciones, montos ni plazos porque no es él quien las ejecuta.
- **Escalamiento.** Solo si el cliente lo pide, dice que no puede pagar nada,
  rechaza todo o la conversación pasa de 30 turnos. El límite de rechazos depende
  del arquetipo, y con el `resistente` es uno solo, porque insistirle no es
  cuidado. El canal de salida lo decide la categoría de tarjeta: platino y black
  llegan a su asesora con cita creada, el resto al Centro de Atención. La
  categoría **nunca** decide si se le ofrece o no una salida.
- **Registro automático.** Cada sesión guarda transcripción, resultado
  (`acuerdo`, `negativa`, `siguiente_paso` o `escalado`) y la fecha acordada. Un
  acuerdo sin fecha lo rechaza la propia base de datos.

**Modelos.** Plan B con free tier: Gemini Flash como primario y Groq como
secundario, con fallback automático y bitácora de cada llamada en `IA_LLAMADA`.
Se usa un modelo rápido para clasificar y sugerir, y el principal para las
respuestas libres. El guard de entrada es Llama Prompt Guard 2 sobre Groq.

**Guardrails en código, no solo en el prompt.** A la salida se limpia cualquier
rastro de «moroso», de urgencia y de «paga ya», porque un modelo generativo tiende
justo a lo que las reglas prohíben.

## 5. Motor diario y notificaciones

`RutaMotor` corre con `@Scheduled` y también a demanda. Cada día hace tres cosas:

1. **Corte cercano.** Avisa a quien está a 5 días de su corte con saldo pendiente.
   Se decide por ciclo y saldo, nunca por el riesgo del cliente.
2. **Aparta la parte** que toca ese día. Si el saldo no alcanza, nace el contexto
   de choque y el aviso «Este mes vino distinto, y está bien», que abre el asesor
   ya sabiendo qué pasó.
3. **Cobra** el día del cobro con lo apartado, suma un mes al récord y avisa
   «Pagado, y a tiempo».

Para la demo se puede simular cualquier día:

```bash
curl -X POST "http://localhost:8080/api/admin/ruta/procesar?fecha=2026-10-18"
```

Cada aviso se guarda en `AVISO`, se ve en la vista 11 y se envía a los
dispositivos registrados. Los tokens de Expo salen por el Expo Push Service y el
resto por Firebase. Sin credenciales de Firebase el envío queda registrado como
`SIMULADO` y la demo sigue funcionando.

## 6. Base de datos

Oracle XE 21c sobre el PDB `XEPDB1`, 40 tablas. Los scripts están en
`backend/src/main/resources/db/`:

| Archivo | Para qué |
|---|---|
| `oracle-schema.sql` | Esquema completo, idempotente. Tablas, funciones de ciclo de corte, vistas de saldo, reincidencia, candidatos a push y dashboard |
| `oracle-seed.sql` | Dataset de prueba, idempotente |
| `h2/schema-h2.sql` · `h2/data-h2.sql` | Los mismos, derivados para correr sin Oracle |
| `00-crear-usuario.sql` | Crea un usuario de aplicación con privilegios mínimos |
| `legacy/` | Esquema original del equipo, del que se conservaron el ledger y las funciones de corte |

**Dataset.** 42 clientes repartidos en tres perfiles crediticios, porque el flujo
se evalúa según el tipo de cliente: 12 impecables, 15 mejorables y 15 fatales, con
sus categorías A1 a E. Encima, 45 cuentas, 70 créditos entre tarjetas, personales,
hipotecarios y bancarios, más planes, apartados, avisos, récord, citas, chats,
ledger de transacciones y bitácora de push. Se genera desde una sola definición
con `node tools/seed/generate-seed.mjs`, que escribe las dos variantes a la vez.

Clave de todos los usuarios de prueba: **`ruta2026`**.

| Usuario | Para mostrar |
|---|---|
| `edgar.gomez` | Caso base de la demo |
| `mauricio.sosa` | No tiene cuenta con el banco, entra por la rama de apertura |
| `carlos.ramirez` | Solo cobra a fin de mes, así que ve «1 parte» |
| `kevin.alas` | Arquetipo resistente, un solo rechazo y el chat escala |
| `lucia.hernandez` | Tarjeta black, escala con cita con su asesora |

`GET /api/admin/clientes` lista los 42 con perfil, arquetipo y si tienen cuenta.

## 7. Cómo correrlo

### Todo de una vez (macOS)

```bash
./scripts/levantar-demo.sh
```

Levanta n8n en Docker, compila y arranca el backend con H2, abre el emulador de
Android y la app. `--sin-app` deja solo n8n y backend; `--reiniciar` vuelve los datos
al dataset original. `./scripts/detener-demo.sh` apaga lo que levantó. En Windows,
sigue los pasos de abajo uno por uno.

### Base de datos

```bash
sqlplus JULIOPALACIOS/<tu-clave>@//localhost:1521/XEPDB1 @backend/src/main/resources/db/oracle-schema.sql
```

Después el seed, del mismo modo, con `oracle-seed.sql`. Conviene poner
`NLS_LANG=AMERICAN_AMERICA.AL32UTF8` antes, para conservar las tildes.

### Backend

Copia `backend/.env.example` a `backend/.env` y rellena la conexión a Oracle y,
si las tienes, las llaves de IA. Ese archivo nunca se sube.

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=oracle
```

Sin Oracle instalado, `mvn spring-boot:run` a secas arranca con H2 en memoria y el
mismo dataset. Sin llaves de IA también arranca: el chat y las sugerencias usan su
guion determinista.

### App móvil

Copia `frontend/.env.example` a `frontend/.env`. Con el emulador de Android, que
es el camino probado:

```bash
adb reverse tcp:8080 tcp:8080
```

```bash
cd frontend
npm install
npx expo start --android
```

En Windows los comandos van en líneas separadas a propósito. El PowerShell que
trae el sistema es la versión 5.1 y no acepta `&&` como separador, así que
encadenarlos falla con `El token '&&' no es un separador de instrucciones válido`.
Si prefieres una sola línea, usa `;` en vez de `&&`.

El túnel de `adb` hace que el emulador alcance el backend como tráfico local, lo
que además evita cualquier problema de firewall. Hay que repetirlo cada vez que se
reinicia el emulador. Con Metro corriendo, la tecla `w` abre la versión web. Para
un celular físico hay que poner la IP de la PC en el `.env`, estar en la misma
Wi-Fi y abrir el puerto 8080.

### Tableros

Con el backend arriba:

| Página | Para qué |
|---|---|
| <http://localhost:8080/api/dashboard.html> | Gestión: clientes por perfil y categoría, cuántos están a cinco días del corte, apartados activos, citas, resultados de las conversaciones con su porcentaje de resolución sin escalar, envíos de push y métricas de IA |
| <http://localhost:8080/api/auditoria.html> | Auditoría: cada evento de la app, el chat, la voz, n8n y el sistema, con una lista de «¿funciona?» que dice qué se probó en las últimas 24 horas y cómo probar lo que falta |
| <http://localhost:8080/api/llamada-emulada.html> | La llamada de voz sin cuentas ni número: suena, se contesta y pasa por n8n y el backend reales |

## 8. Contrato de la API

Todo cuelga de `/api` y, salvo el ingreso, pide `Authorization: Bearer`.

| Vista | Ruta |
|---|---|
| 01 | `POST /auth/ingreso` |
| 02 · 10 · 10b | `GET /clientes/yo/inicio` |
| 03 | `POST /fecha-cobro/frecuencia` |
| 04 | `GET /fecha-cobro/opciones?frecuencia=` |
| 05 | `POST /fecha-cobro` |
| 06 | `GET /apartado/creditos` |
| 07 | `GET /apartado/partes?credito=` |
| 08 | `GET /apartado/origen?credito=&partes=` |
| 09 | `POST /apartado` |
| 08a · 08b | `GET /cuenta-digital/oferta` · `GET /cuenta-digital/contrato` · `POST /cuenta-digital` |
| 02 | `POST /productos/{id}/activar` |
| 11 | `GET /clientes/yo/avisos` · `POST /clientes/yo/avisos/leidos` |
| 12 | `GET /clientes/yo/record` |
| 13–15 | `POST /asesor/sesiones` · `POST /asesor/sesiones/{id}/mensajes` · `POST /asesor/sesiones/{id}/fin` |
| push | `POST /dispositivos` |
| IA | `GET /ai/sugerencias/frecuencia` · `/fecha` · `/partes` · `GET /ai/metrics` |
| admin | `POST /admin/ruta/procesar` · `GET /admin/metrics` · `GET /admin/notificaciones` · `GET /admin/clientes` · `GET /admin/voz/llamadas` · `POST /admin/demo/telefono` |
| auditoría | `GET /admin/auditoria?canal=&nivel=&q=&cliente=&desde=` · `GET /admin/auditoria/resumen` · `DELETE /admin/auditoria` |
| voz (n8n, `X-Voz-Key`) | `GET /voz/llamadas/pendientes` · `POST /voz/llamadas` · `POST /voz/fecha/propuesta` · `POST /voz/fecha/confirmar` · `POST /voz/llamadas/resultado` |

El detalle de cada respuesta está en `frontend/src/api/tipos.ts`, que es la fuente
de verdad del contrato, y en `backend/README.md`.

## 9. Reglas de contenido

Son obligatorias en todo texto del sistema, venga de la app, de un aviso o del
modelo:

- Nunca la palabra «moroso», ni hablar de caer en mora.
- Voz en «tú», cálida, que no exige pagar.
- La persona decide con opciones, no escribiendo, salvo en el chat.
- Lo que no fue a tiempo se muestra como fecha de salida, nunca como castigo.
- Los avisos confirman lo que ya pasó y jamás recuerdan pagar.
- Los cierres nunca quedan ambiguos.

Del sistema de diseño: el amarillo es acción y no decoración, el color nunca va
solo, los montos usan cifras tabulares, nada interactivo baja de 44 puntos y
ninguna decisión cuesta más de tres toques.

## 10. Por qué está diseñado así

Tres decisiones que no son de estilo, sino de evidencia.

**El chat nunca inicia el contacto.** Un ensayo aleatorizado sobre 8.019 clientes
al día, en un banco del mismo tipo, midió el efecto de mensajes conductuales sobre
clientes sanos y encontró cero, estimado con precisión. El mismo estudio, sobre
7.029 clientes ya atrasados, sí encontró efecto, concentrado en los atrasados con
buen historial. Por eso el chat solo se abre cuando la persona lo pide o cuando ya
ocurrió un evento.

**El mensaje trae una acción ejecutable ahora.** Los metaanálisis de mensajes de
miedo muestran que, sin una ruta de acción concreta, el mensaje empeora el
resultado. De ahí que el asesor no termine en «¿agendamos?» sino en algo que se
resuelve en el mismo turno.

**La llamada sí inicia el contacto, y por eso no recuerda pagar.** Es la única
excepción a la regla anterior, y se sostiene porque no es un mensaje: cambia la
fecha. Con asignación aleatoria, un solo día de desfase entre el ingreso y el
vencimiento sube 10 puntos el pago tardío (Dahan, 2022), así que alinear la fecha
ataca una causa y no un síntoma. La evidencia viene de otros productos y países:
por eso la llamada se mide contra un grupo sin llamada antes de escalarla.

**Norma social en vez de urgencia.** En el mismo ensayo, el ángulo de urgencia no
fue significativo. El nombre propio, del cliente y del asesor, sí movió resultados
en varios estudios. El copy del sistema sigue esas dos filas.

## 11. Estructura del repositorio

```
backend/     Spring Boot, la API, la IA, el motor diario y los scripts de base de datos
  src/main/java/com/bancoagricola/ruta/
    config/ domain/ repository/ dto/ service/ ai/ web/
  src/main/resources/
    application.yml  db/  static/ (dashboard, auditoría, llamada emulada)
  tools/seed/        generador del dataset en Oracle y H2
frontend/    App React Native con Expo
  src/api/           contrato y cliente HTTP
  src/screens/       las vistas, por sección
  src/components/    biblioteca de interfaz
  src/theme/         tokens extraídos de Figma
  design/            referencias y especificaciones del diseño
voz/         guion de la asistente, flujos de n8n y asistente de Vapi
infra/n8n/   n8n local en Docker
scripts/     levantar-demo.sh y detener-demo.sh
presentacion/ deck v3 (pptx con el video y pdf de respaldo) y guion de defensa
DEMO.md      cómo presentar y probar la demo
```

## 12. Estado y pendientes

Funciona de punta a punta: app, backend, Oracle, IA con llaves reales, motor
diario, avisos, llamada emulada, auditoría y tableros. Queda pendiente lo siguiente.

- **Push reales en el dispositivo.** Falta una build de desarrollo con
  credenciales de Firebase o el identificador de proyecto de EAS. Mientras tanto
  los envíos quedan como `SIMULADO` y los avisos se ven dentro de la app.
- **Voz.** Implementado en `voz/` y `/api/voz`, con n8n y Vapi, y probado de punta a
  punta con la llamada emulada. Falta la llamada real: un número de Twilio, Vonage o
  Telnyx importado en Vapi, un túnel público al backend y los flujos publicados en
  n8n Cloud. Decisiones abiertas en `voz/README.md` §7.
- **WhatsApp.** Mismo caso que la voz.
