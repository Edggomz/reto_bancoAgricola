# Ruta: guion de defensa ante Bancoagrícola (versión 3)

Este documento no se lee en la presentación: sirve para prepararla. Trae el guion
minuto a minuto de la versión 3, la demo en vivo y las preguntas difíciles, con las
que agregan la llamada, el interés y la auditoría.

---

## 1. Las piezas

- **`Ruta_Presentacion_Bancoagricola_v3.pptx`**: 12 diapositivas para unos 7:25
  minutos. El guion de cada una va en las **notas del orador**.
- **`Ruta_Presentacion_Bancoagricola_v3.pdf`**: respaldo por si el proyector no tiene
  las fuentes o falla el video. No reproduce el video. Si alguien del equipo tiene
  PowerPoint, *Archivo → Exportar → PDF* da una copia idéntica al `.pptx`.
- **`Ruta_Demo_v2.mp4`**: el mismo video de la v2, ya incrustado en la diapositiva 5.
- **La demo en vivo**: [`DEMO.md`](https://github.com/Edggomz/reto_bancoAgricola/blob/main/DEMO.md)
  en el repo. Un comando levanta todo.

### Qué cambió respecto a la versión 2 y por qué

| Antes | Ahora | Por qué |
|---|---|---|
| La llamada no aparecía | **Diapositiva 7**: una llamada que mueve la fecha, con la pantalla real de la llamada | Es el canal para quien no usa la app, y vale 20 puntos de la rúbrica |
| Nada sobre cómo se verifica | **Diapositiva 10**: todo queda registrado, con la auditoría real de una llamada | El comité pregunta «¿cómo sé que funciona?»; ahora se responde con evidencia en pantalla |
| Diapositiva 4: «Mismo monto, mismo plazo» | «Mismo plazo, sin sorpresas» | Si la primera cuota se corre unos días, esos días llevan interés una sola vez. Decir «mismo monto» ya no es exacto |
| Solidez: «36 tablas» y «canales de voz» pendientes | «40 tablas» y «llamada real con número» pendiente | La llamada ya funciona emulada; lo que falta es el número y Vapi |
| 10 diapositivas, 6:30 | 12 diapositivas, 7:25 | Si el tiempo es de 6:30, salten la 10 (auditoría) y acorten la 7 |

---

## 2. Guion minuto a minuto (7:25)

**1 · Portada (0:00–0:25).** Preséntense, nombren el reto y digan la frase ancla tal cual:
> «No rescatamos a quien ya se atrasó. Evitamos que un cliente sano llegue a atrasarse.»

**2 · El problema: el calendario (0:25–1:05).** Cuenten a Edgar: le pagan el 15 y el
30, y su cuota se cobra el 28. *«No es falta de voluntad: es el calendario.»* Toda la
cobranza actual actúa después del vencimiento; el tramo de antes, el único sin costo,
hoy está vacío.

**3 · La evidencia (1:05–2:00).** Tres estudios, no una intuición: la educación
financiera explica el 0.1 % del comportamiento; los mensajes a clientes al día
tuvieron efecto cero; apartar el propio dinero con un compromiso subió el ahorro 81
puntos. Cierre: *«por eso Ruta no le habla al cliente sano: le cambia el sistema».*

**4 · La solución (2:00–2:35).** Los cuatro movimientos, sin tecnicismos. Al decir
«mover la fecha», agreguen: *«si la primera cuota se corre unos días, el interés de
esos días se dice antes y se cobra una sola vez»*. Mejor decirlo ustedes que
explicarlo cuando lo pregunten.

**5 · Demo (2:35–3:25).** Den clic al video y narren encima. *«No es un mockup: son las
pantallas reales, conectadas al backend que valida cada regla.»* El video es de la v2 y
no muestra el interés en la vista 04; si lo preguntan, la demo en vivo sí lo muestra.

**6 · Si el mes viene distinto (3:25–4:00).** Lean el aviso: *«Este mes vino distinto,
y está bien.»* La IA entiende la intención; quien ejecuta es el backend.

**7 · La llamada (4:00–4:50).** *«Hay clientes que no usan la app. A ellos el banco los
llama, y la llamada hace lo mismo que la app: mover la fecha.»*
- Dos preguntas: cómo y qué día le llega el dinero.
- Propone un día en que ya le llegó y dice el interés antes; solo cambia con un sí.
- Si le preguntan «¿es una estafa?», responde que nunca pide claves. Con otra persona al
  teléfono no habla del préstamo.
- Remate, con la tarjeta de abajo: *«no es un recordatorio: ataca la causa. Un solo día
  de desfase sube 10 puntos el pago tardío.»*
- Díganlo antes de que lo pregunten: *«en la demo la llamada es emulada; la voz real
  con número es el siguiente paso».*

**8 · La propuesta de valor (4:50–5:30).** La autocorrección, con calma: *«Pensamos que
esto era educación financiera. No es exacto: un curso no cambia el comportamiento.
Practicar la buena decisión cada mes, sí.»*

**9 · Por qué al banco (5:30–6:05).** Menos cartera en cobranza, más producto por
cliente y una relación que no empieza con un cobro. Los números del tablero son de
prueba; lo que importa es qué mide.

**10 · Auditoría (6:05–6:35).** *«Todo lo que acaban de ver quedó registrado.»* Señalen
la llamada a Samuel paso a paso, con el tiempo de cada respuesta. Tres ideas: cada paso
deja rastro, solo se registra lo que de verdad se guardó, y nunca claves ni tokens.
*Si el tiempo es corto, esta se salta.*

**11 · Solidez técnica (6:35–7:00).** Funciona de punta a punta: app, API con reglas,
Oracle, asesor con IA, motor diario, llamada por n8n y auditoría. Lean los pendientes
en voz alta.

**12 · Piloto y cierre (7:00–7:25).** Pidan un **piloto controlado**: Legal, SSF y
datos; tres ciclos de cobro con un grupo de control; y la decisión con los números del
banco. Repitan la frase ancla y den las gracias.

---

## 3. Demo en vivo (si les dan pantalla)

La guía completa, con qué hacer si algo falla, está en `DEMO.md`. En corto:

1. Antes: `./scripts/levantar-demo.sh --reiniciar` en la Mac.
2. **App:** `edgar.gomez` / `ruta2026` → Cambiar fecha → Quincena y fin de mes → Día 3
   (se ve el interés) → Apartar en 2 partes.
3. **Llamada:** Samuel Quijada, «Cuándo suena: en 5 segundos» → Contestar → «Sí, soy yo»
   → «¿Es una estafa?» → «Sí, dime» → «Pensión» → «El 25» → «Sí, cámbiala» → «En la
   agencia».
4. **Auditoría:** la historia completa y la lista «¿funciona?».

---

## 4. Premortem: por qué esto podría NO funcionar (y qué responder)

**«Esto le quita ingreso al banco (intereses y comisiones de mora).»**
Gestionar la mora suele costar más que el ingreso marginal de un cliente que se atrasa
de vez en cuando, y el flujo abre cuenta digital y depósito a plazo. Si piden el costo
exacto de cobranza por cliente: «no lo tenemos; es parte de lo que mediría el piloto».

**«¿Por qué no simplemente débito automático?»**
El débito cobra en la fecha de siempre, haya o no dinero. Ruta primero mueve el cobro al
día en que ya hay dinero y reparte la cuota entre sus pagos. Lo apartado sigue siendo
suyo hasta el día del cobro.

**«¿Cambiar la fecha le cuesta algo al cliente?»**
Sí, y se dice antes. Si la primera cuota se corre unos días, esos días llevan interés
una sola vez; la app lo muestra en cada día antes de confirmar y la llamada lo dice
antes de pedir el sí. Si no lo acepta, el servidor no cambia nada. Si el día nuevo
queda antes, no hay interés.

**«¿Y si el cliente abusa del sistema para nunca pagar?»**
No puede: solo se ofrecen días posteriores a un pago real, nunca del 28 al 31, el
primer cobro nuevo cae al menos 14 días después del actual, y después de un cambio la
fecha queda fija 6 meses. Apartar congela el dinero, pero no lo abona hasta el cobro.

**«La evidencia dice que los mensajes no funcionan en clientes sanos. ¿Por qué llaman?»**
Porque la llamada no es un mensaje: no recuerda pagar, cambia la fecha. Con asignación
aleatoria, un día de desfase entre ingreso y vencimiento sube 10 puntos el pago tardío
(Dahan, 2022). Es evidencia de otro producto y otro país, así que la llamada se mide
contra un grupo sin llamada antes de escalarla.

**«Una llamada automática a una persona mayor, ¿no parece estafa?»**
Por eso se presenta como asistente virtual, dice que la llamada queda grabada, nunca
pide claves ni números de tarjeta y, si la persona duda, le dice que cuelgue y llame ella
misma a Telebanca. Solo llama de lunes a viernes, de 8:00 a 18:00, y con otra persona al
teléfono no menciona el préstamo.

**«¿La llamada de la demo es real?»**
Es emulada: el navegador pone la voz y sigue el guion, pero manda a n8n exactamente lo
que mandaría Vapi, y el backend aplica las reglas reales. La voz real necesita una
cuenta de Vapi y un número de Twilio, Vonage o Telnyx.

**«¿Cuánto cuesta cada llamada?»**
Con Twilio y Vapi, una llamada de 3 minutos a un celular de El Salvador sale en torno a
**$1.30** (precios públicos al 13 sep 2026). A escala hay que negociarlo; el piloto
diría cuántas llamadas hacen falta por fecha movida.

**«¿La IA puede prometer condiciones que el banco no autorizó?»**
No. En el chat, el modelo solo clasifica la intención; en la llamada, los montos y las
fechas los redacta el backend y la asistente los lee tal cual. Quien ejecuta es siempre
el backend.

**«¿Cómo sabemos que lo que muestran funciona?»**
Con la auditoría: cada paso de la app, el chat, la llamada y n8n queda registrado, con
su hora y su tiempo. Solo se registra lo que de verdad quedó guardado, y hay una lista
«¿funciona?» que se puede revisar en vivo.

**«¿Qué pasa con la privacidad?»**
El registro nunca guarda claves ni tokens y enmascara el teléfono. Las conversaciones del
asesor guardan transcripción y resultado. Antes de producción hay que alinear la
retención con la política de datos del banco y con la grabación de llamadas.

**«¿Esto cumple la regulación de la SSF?»**
Es el pendiente más serio: falta la revisión legal y de cumplimiento, sobre todo del
cambio de fecha con interés y de apartar fondos. Por eso es el paso 1 del piloto.

**«¿Qué pasa si a alguien le cae un gasto imprevisto?»**
El sistema registra un contexto de choque y manda «este mes vino distinto, y está bien»,
que abre el asesor sabiendo qué pasó. Es la diapositiva 6.

**«Es un prototipo con 42 clientes de prueba.»**
Sí, y no conviene decir lo contrario: no hay pruebas de carga ni de concurrencia. Es
parte de lo que respondería un piloto acotado.

**«¿Los números del tablero son reales?»**
No: salen del dataset de prueba. Lo que demuestran es que el banco puede medir cada
resultado desde el primer día.

---

## 5. Si preguntan por números que no tenemos

> «No lo tenemos medido todavía: es exactamente lo que el piloto mediría antes de escalar.»

---

## 6. Fuentes citadas

- Fernandes, D., Lynch, J. G., & Netemeyer, R. G. (2014). *Financial Literacy, Financial
  Education, and Downstream Financial Behaviors.* Management Science, 60(8).
- Barboni, G., Cárdenas, J.-C., & De Roux, N. (2022). *Behavioral Messages and Debt
  Repayment.* Ensayo aleatorizado con un banco en Colombia (8,019 clientes al día y
  7,029 atrasados).
- Ashraf, N., Karlan, D., & Yin, W. (2006). *Tying Odysseus to the Mast: Evidence from a
  Commitment Savings Product in the Philippines.* Quarterly Journal of Economics, 121(2).
- Dahan (2022). *Water Resources Research*, 58(1). En Jerusalén, la fecha de
  vencimiento de la factura de agua se asigna al azar respecto al día de cobro de
  prestaciones: un día de desfase sube 10 puntos el pago tardío.
- Precios de voz: Twilio (El Salvador) y Vapi, consultados el 13 sep 2026.
