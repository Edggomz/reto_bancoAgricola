# Ruta · Bancoagrícola — App móvil

Módulo de **prevención de mora** dentro de Banca Móvil, hecho en React Native
(Expo SDK 57) como réplica fiel del Figma «Ruta · Sistema y Pantallas»
(<https://www.figma.com/design/ktdDxdfWbdPNMekDe8BOXq>), páginas `02 · Flujo UX`
y `03 · Pantallas`.

La app **no trae datos propios**: todo lo que es de la persona (nombre, saldos,
productos, fechas, avisos, lo que dice el asesor) llega del backend
`BackEnd-bancoAgricola` (Spring Boot + Oracle). Sin API configurada, cada
vista muestra el espacio vacío donde irá su dato.

---

## 1. Correrla

```bash
npm install
cp .env.example .env        # y pon EXPO_PUBLIC_API_URL=http://localhost:8080/api
npx expo start --android    # «a» emulador · «w» web
```

- **Backend real:** `BackEnd-bancoAgricola` con `mvn spring-boot:run
  -Dspring-boot.run.profiles=oracle`, o sin perfil para H2.
- **Emulador Android**, que es el camino probado: arranca el AVD y abre el túnel
  con `adb reverse tcp:8080 tcp:8080` antes de `npx expo start --android`. Con el
  túnel, `http://localhost:8080/api` funciona igual en el emulador y en web, así
  que no hay que cambiar el `.env` al alternar entre los dos.
- **Celular físico:** pon la IP de la PC en `.env`, por ejemplo
  `http://10.74.10.194:8080/api`, conéctate a la misma Wi-Fi y asegúrate de que
  el puerto 8080 no esté bloqueado por el firewall.
- **Usuarios de prueba:** clave `ruta2026`. `edgar.gomez` (caso base),
  `mauricio.sosa` (sin cuenta con nosotros), `carlos.ramirez` (solo fin de mes →
  1 parte), `kevin.alas` (resistente), `lucia.hernandez` (black, escala con cita).
  La lista completa: `GET /api/admin/clientes`.
- **Con datos de ejemplo sin backend:** `npm run api:ejemplo` (contrato anterior;
  no cubre las rutas nuevas del inicio).
- **Teléfono físico:** Expo Go, misma Wi-Fi, escanear el QR; si el Wi-Fi bloquea,
  `npx expo start --tunnel`.
- **Menú de demostración** (fuera de Figma): mantener presionado el logo.

---

## 2. Contrato con el backend

Tipos en [`src/api/tipos.ts`](src/api/tipos.ts), rutas en
[`src/api/servicios.ts`](src/api/servicios.ts). Los montos van como número;
fechas y textos, ya redactados.

| Vista | Método y ruta | Qué devuelve |
|---|---|---|
| 01 | `POST /auth/ingreso` `{usuario, clave}` | `{token}` (se manda como `Bearer`) |
| 02 · 10 · 10b | `GET /clientes/yo/inicio` | cliente, `cuenta` (**null** si no tiene), `creditos` (tarjetas, personales, hipotecarios, bancarios), `credito` principal, `ruta` (activa / solo fecha / nula), `record`, `productos` y `avisosSinLeer` |
| 03 | `POST /fecha-cobro/frecuencia` `{frecuencia}` | 204 — la respuesta se guarda apenas se elige |
| 04 | `GET /fecha-cobro/opciones?frecuencia=` | día de hoy y grupos de días (varían por frecuencia), cada uno con «desde» y nota |
| 05 | `POST /fecha-cobro` `{frecuencia, dia}` | `{dia, desde, operacion}` |
| 06 | `GET /apartado/creditos` | créditos apartables (con uno solo, 06 se salta) |
| 07 | `GET /apartado/partes?credito=` | crédito, sugerencia y el calendario de cada opción permitida (1, 2, 3 o 4 partes según cómo le pagan) |
| 07 → 08 / 08a | `GET /apartado/origen?credito=&partes=` | cuenta de donde se aparta (nula = abrir cuenta) y los pasos con esas partes |
| 09 | `POST /apartado` `{credito, partes, automatico}` | filas del resumen |
| 08a · 08b | `GET /cuenta-digital/oferta`, `GET /cuenta-digital/contrato`, `POST /cuenta-digital` | condiciones, resumen y documentos, firma |
| 02 | `POST /productos/{id}/activar` | activa un producto disponible (p. ej. `deposito-plazo`), que desaparece del inicio |
| 11 | `GET /clientes/yo/avisos` · `POST /clientes/yo/avisos/leidos` | avisos con fecha ISO y destino; al verlos quedan leídos |
| 12 | `GET /clientes/yo/record` | racha, próximo +1, hitos, explicación y lo que suma |
| 13–15 | `POST /asesor/sesiones` `{aviso}` → `{sesion, mensajes, sugerencias}` · `POST /asesor/sesiones/:id/mensajes` `{texto}` → `{mensajes, sugerencias}` · `POST /asesor/sesiones/:id/fin` | el asesor (ver §3) |
| IA | `GET /ai/sugerencias/frecuencia` · `/fecha?frecuencia=` · `/partes?credito=` | opción sugerida y motivo; se marca «· Sugerido» y se muestra el motivo, la persona decide |
| push | `POST /dispositivos` `{token, plataforma}` | registra el teléfono para los avisos |

---

## 3. Qué hace cada vista con los datos

- **Inicio (02 · 10 · 10b).** Nombre e iniciales del cliente; saldo de su cuenta
  o, si no tiene cuenta con nosotros, una tarjeta oscura que lo dice y ofrece
  **abrirla** (08a → 08b → vuelve al inicio). Los productos bancarios se pintan
  en una cuadrícula con lo que la persona tenga (tarjeta, crédito personal,
  hipotecario, bancario). «Mi ruta» aparece activa o solo con la fecha; la
  tarjeta **«Tu récord»** lleva a la vista 12. **«Tienes productos disponibles»**
  solo se muestra si queda algo por activar: «Cambiar fecha de cobro» desaparece
  al confirmar la fecha o activar la ruta; «Depósito a plazo digital» se activa en
  una ventana de confirmación y desaparece. El menú «···» abre Avisos (con los
  no leídos), Mi récord y Cerrar sesión.
- **¿Qué día te pagan? (03).** Las cuatro opciones; la que la IA sugiere según
  los abonos reales lleva «· Sugerido» y su motivo. Al continuar, la respuesta
  se manda al backend.
- **¿Qué día te queda mejor? (04 · 05).** Los días llegan del backend y cambian
  con la frecuencia (quincena y fin de mes → 18/19 y 3/4; solo fin de mes → 3/4;
  cada semana → los lunes tras cada viernes de pago; variable → los días en que
  suele entrar más dinero). Nada viene marcado; al elegir se ve desde cuándo
  aplica. «Ahora no» vuelve al inicio con la fecha ya cambiada (10b).
- **¿En cuántas partes? (07).** Solo las partes que permite su forma de pago
  (quien cobra una vez al mes ve «1 parte»). El calendario y los montos los
  calcula el backend; la IA sugiere una opción.
- **¿Automático? (08 · 09).** La cuenta origen y los pasos vienen con las partes
  elegidas (con una parte, el paso es «apartamos tu cuota completa»). El resumen
  de 09 es lo que quedó guardado.
- **Avisos (11).** Todo aviso del sistema (cambio de fecha, ruta lista, parte
  apartada, cuota completa, pagado, corte cercano, choque, cuenta abierta,
  depósito, cita) llega por push y queda en esta vista.
- **Mi récord (12).** Racha, próximo +1 (la próxima cuota), camino a 24 de 24 y
  lo que suma; nada como castigo.

---

## 4. El asesor bancario

El chat **es** el asesor: se abre desde el botón «Asesor» o desde un aviso de
choque (con contexto). Todo lo que dice llega del backend, que clasifica la
intención con Gemini Flash (fallback Groq) y **ejecuta la acción en el mismo
turno**: mover la fecha, apartar en partes, dejarlo en automático o agendar con
la asesora. Las sugerencias (chips) también las manda el backend.

- Resuelve primero; escala a una persona solo si el cliente lo pide, no puede
  pagar, rechaza todo o la conversación se alarga. Si dice que no, no insiste.
- Temas ajenos y intentos de cambiar las reglas se redirigen con amabilidad.
- **‹** deja la conversación en pausa; **×** pregunta «¿Terminamos la
  conversación?» (15) si ya se escribió algo. La transcripción, el resultado y
  la fecha acordada quedan registrados en el backend.

---

## 5. Avisos push

[`src/avisos/`](src/avisos) queda listo para recibirlos:

- `push.ts` pide permiso, registra el token en `POST /dispositivos` y escucha
  los avisos. Al tocar uno, abre su destino (`MiRecord` o el asesor con el
  aviso). La carga que manda el backend en `data` es
  `{ id, fecha, destino: { vista: 'record' } | { vista: 'asesor', aviso } | null }`.
- El backend envía tokens de Expo (`ExponentPushToken[...]`) por el Expo Push
  Service y cualquier otro token por Firebase; sin credenciales de Firebase el
  envío queda registrado como `SIMULADO` y el aviso igual aparece en la vista 11.
- **Para recibir push de verdad** hace falta una build de desarrollo (Expo Go
  ya no los recibe en Android), `extra.eas.projectId` en `app.json` y, para FCM,
  las credenciales en el backend. Hasta entonces la capa se apaga sola.

---

## 6. Cómo se garantiza que se parezca a Figma

Todo sale del archivo de Figma por el puente `figma-console` (plugin *Figma
Desktop Bridge*), sin gastar la cuota del MCP oficial.

| Paso | Qué | Dónde queda |
|---|---|---|
| 1 | `npm run figma:recibir` y pegar `scripts/figma-export.js` en `figma_execute` | `design/` |
| 2 | `npm run figma:esquemas` | `design/specs/outline/*.txt` |
| 3 | `npm run tokens` | `src/theme/tokens.ts` (no editar a mano) |
| 4 | `npm run qa:web` (con la API de ejemplo) | `visual-qa/lado-a-lado`, `visual-qa/diff` |
| 5 | `EXP_URL=exp://<ip>:8081 npm run qa:android` | `visual-qa/resumen-android.png` |
| 6 | `EXP_URL=… npm run qa:pruebas` | 28 pruebas de uso: toques dobles, dos botones a la vez, botones inactivos, «atrás» y salidas |

Decisiones de fidelidad: auto-layout → Flexbox 1:1; Open Sans en sus cuatro
pesos; en pantallas de menos de 390 pt se diagrama a 390 y se reduce en
proporción; la barra de estado es la del sistema. Lo que no estaba en Figma y se
diseñó con las mismas reglas: la tarjeta «sin cuenta», la tarjeta «Tu récord»,
el menú «···», la ventana de activación de un producto y la marca «· Sugerido».

---

## 7. Vistas y navegación

| # | Vista | Ruta | Toque → destino |
|---|---|---|---|
| 01 | Ingreso | `Ingreso` | CONTINUAR (con usuario y clave) → 02 |
| 02 · 10 · 10b | Inicio | `Inicio` | ACTIVAR (fecha) → 03 · ACTIVAR (depósito) → ventana · APARTAR → 06 · ABRIR MI CUENTA → 08a · Tu récord → 12 · ··· → menú · Asesor ⇒ 13 |
| 03 | ¿Qué día te pagan? | `QueDia` | ninguna opción marcada; CONTINUAR guarda la respuesta → 04 |
| 04 · 05 | Elige tu fecha · Listo | `EligeFecha` | ningún día marcado; CONFIRMAR → ventana 05 → 06 o inicio |
| 06 | ¿Qué cuota apartamos? | `QueCuota` | tocar el crédito → 07 |
| 07 | ¿En cuántas partes? | `CuantasPartes` | ninguna marcada; CONTINUAR → 08 o 08a (lo decide el banco) |
| 08 · 09 | ¿Automático? · Tu ruta quedó lista | `Automatico` | ACTIVAR → ventana 09 → inicio · Ahora no → inicio |
| 08a · 08b | Abre tu cuenta · Revisa y firma | `AbreCuenta`, `RevisaFirma` | la casilla empieza sin marcar; FIRMAR → 08 (o inicio si venía de ahí) |
| 11 | Avisos | `Avisos` | ‹ vuelve · aviso con destino → 12 o asesor |
| 12 | Mi récord | `MiRecord` | ‹ · Asesor ⇒ 13 |
| 13 · 14 · 14b · 15 | Asesor bancario | `Asesor` | ver §4 |

---

## 8. Pendiente

- Build de desarrollo y credenciales de FCM / `projectId` de EAS para los push.
- Voz (speech-to-speech): reservado para después.
- iPhone: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.
