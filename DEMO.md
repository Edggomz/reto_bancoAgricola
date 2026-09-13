# Demo de Ruta: cómo levantarla, presentarla y comprobar que funciona

Todo corre en la laptop, sin cuentas ni número de teléfono. La app va en el emulador
de Android, la llamada suena en el navegador y cada paso queda en la auditoría.

## 1. Una sola vez por máquina

- **Docker Desktop**, para n8n.
- **JDK 21** (o 17). Con el JDK 26 de Homebrew, Lombok no compila.
- **Android Studio** con un emulador (`Pixel_6` o `S23_Ultra`) y **Node 20+**.
- **Chrome**, para la voz de la asistente y el micrófono.

## 2. Levantar todo (macOS)

```bash
./scripts/levantar-demo.sh
```

Tarda uno a tres minutos la primera vez. Deja esa terminal abierta: ahí corre la app,
y la tecla `r` la recarga. Para apagar todo: `./scripts/detener-demo.sh`.

| Opción | Para qué |
|---|---|
| `--reiniciar` | Vuelve los datos al dataset original. **Úsala antes de presentar**, para que Edgar y Samuel no tengan ya la fecha cambiada |
| `--sin-app` | Solo n8n y backend, por ejemplo para probar la llamada sin el emulador |
| `--reimportar` | Vuelve a cargar los flujos y las credenciales en n8n |

En Windows no hay script: los pasos a mano están en el [README](README.md#7-cómo-correrlo).

## 3. Qué abrir

| Qué | Dónde |
|---|---|
| App móvil | El emulador. Usuario `edgar.gomez`, clave `ruta2026` |
| Llamada emulada | <http://localhost:8080/api/llamada-emulada.html> |
| Auditoría | <http://localhost:8080/api/auditoria.html> |
| Dashboard de gestión | <http://localhost:8080/api/dashboard.html> |
| n8n local | <http://localhost:5678> |

## 4. Usuarios de prueba

Todos con la clave `ruta2026`.

| Usuario | Para mostrar |
|---|---|
| `edgar.gomez` | La app completa: le pagan el 15 y el 30 y su cuota se cobra el 28 |
| `samuel.quijada` | **La llamada**: no usa la app, cobra una pensión. Termina con la agencia más cercana |
| `mauricio.sosa` | No tiene cuenta: abre una dentro del flujo de apartar |
| `carlos.ramirez` | Solo cobra a fin de mes: ve «1 parte» |
| `mariana.lopez` | Ya cambió su fecha: la llamada le dice desde cuándo puede volver a hacerlo |
| `kevin.alas` | Arquetipo resistente: con un solo rechazo, el chat escala |
| `lucia.hernandez` | Tarjeta black: escala a su asesora con cita |

## 5. El recorrido en vivo (5 minutos)

Antes de empezar: `./scripts/levantar-demo.sh --reiniciar`, la app abierta en el
emulador y la llamada y la auditoría abiertas en dos pestañas de Chrome.

**1 · La app (2 min).** Entra como `edgar.gomez`.
- El inicio pone el saldo primero; la cuota «se cobra el 28».
- Toca **Cambiar fecha de cobro** → «Quincena y fin de mes» → **Día 3**. Antes de
  confirmar se ve el interés de los días que se corre, una sola vez.
- **Aceptar y confirmar** → la vista 05 lo deja escrito → **Apartar mi cuota** en 2
  partes, en automático.

**2 · La llamada (2 min).** Para quien no usa la app.
- En la llamada emulada elige a **Samuel Quijada** y «Cuándo suena: en 5 segundos».
  Toca **Llamar al cliente** y muestra el teléfono: suena solo.
- **Contestar** → «Sí, soy yo» → pregunta «¿Es una estafa?»: responde que nunca pide
  claves y vuelve a su pregunta → «Sí, dime» → «Pensión» → «El 25» → «Sí, cámbiala» →
  «En la agencia».
- Señala la columna derecha: en qué paso va, el formulario que se llena y cuánto tarda
  cada respuesta (menos de 300 ms, pasando por n8n).

**3 · La auditoría (1 min).**
- En <http://localhost:8080/api/auditoria.html> está toda la historia: el ingreso de
  Edgar, su fecha, su apartado, los avisos, y la llamada de Samuel paso a paso.
- La lista **¿Funciona?** marca con ✓ lo que se probó en las últimas 24 horas. Toca
  una fila para ver el detalle.

## 6. Si algo falla

| Síntoma | Qué hacer |
|---|---|
| La app dice «No pudimos cargar tus datos» | El emulador perdió el túnel: `adb reverse tcp:8080 tcp:8080` y recarga con `r` |
| La app se queda en blanco | En la terminal de Expo, `r`. Si sigue, vuelve a correr el script |
| La llamada dice «No pude hablar con n8n» | Abre Docker Desktop y corre `./scripts/levantar-demo.sh --sin-app` |
| «A este cliente no se le llama ahora» | Ese cliente no tiene un crédito con cuota. Usa Samuel, Edgar o Diego |
| La llamada no propone fecha, solo dice desde cuándo se puede cambiar | Ya se cambió en esta sesión. `--reiniciar` o usa otro cliente |
| La asistente no habla | Marca «La asistente habla en voz alta» y usa Chrome; revisa el volumen |
| El micrófono no entiende | Contesta con los botones o escribiendo: el resultado es el mismo |
| Algo raro que no se explica | La auditoría muestra el error con su ruta; el backend escribe en `.demo-logs/backend.log` |

## 7. Qué es real y qué es emulado

Conviene decirlo antes de que lo pregunten.

- **Real:** la app, el backend con todas sus reglas, n8n y la auditoría. La llamada
  emulada manda a n8n exactamente los mensajes que mandaría Vapi.
- **Emulado:** en la llamada, el navegador pone la voz y sigue un guion que entiende
  las respuestas típicas y las preguntas más comunes; no es conversación libre. La voz
  real, con transcripción y modelo, es Vapi, y necesita cuenta y un número. Ver
  [`voz/PROBAR.md`](voz/PROBAR.md).
- **Ilustrativo:** los 42 clientes, sus saldos y tasas. Los push quedan como
  `SIMULADO` sin credenciales de Firebase.
- **La base es H2 en memoria:** al reiniciar el backend todo vuelve al dataset.
