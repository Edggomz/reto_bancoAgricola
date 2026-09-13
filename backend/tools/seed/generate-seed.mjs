// Genera el dataset de prueba de Ruta en dos dialectos desde una sola definición:
//   src/main/resources/db/oracle-seed.sql  (Oracle, idempotente)
//   src/main/resources/db/h2/data-h2.sql   (H2, perfil por defecto del backend)
//
// Uso:  node tools/seed/generate-seed.mjs
//
// Las fechas que el push y el ciclo de corte necesitan se escriben como
// expresiones relativas a SYSDATE (se evalúan al cargar), así la demo de
// «5 días antes del corte» se reproduce cualquier día. Las etiquetas de copy
// («18 de octubre») se calculan al generar.

import { createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const SALIDAS = {
  oracle: resolve(ROOT, 'src/main/resources/db/oracle-seed.sql'),
  h2: resolve(ROOT, 'src/main/resources/db/h2/data-h2.sql'),
};
const CONTRASENA_DEMO = 'ruta2026';
const DIAS_AVISO_PUSH = 5;
const HOY = new Date(new Date().toDateString());

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------
function prng(seed) {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const azar = prng(20260913);
const entre = (min, max) => min + Math.floor(azar() * (max - min + 1));
const elegir = (lista) => lista[Math.floor(azar() * lista.length)];
// Canal de voz: generador aparte para no mover la secuencia del dataset existente.
const azarVoz = prng(20260914);
const entreVoz = (min, max) => min + Math.floor(azarVoz() * (max - min + 1));
const elegirVoz = (lista) => lista[Math.floor(azarVoz() * lista.length)];
const dinero = (v) => Math.round(v * 100) / 100;
const sha256 = (s) => createHash('sha256').update(s, 'utf8').digest('hex');
const slug = (s) =>
  s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
const render = (plantilla, vars) =>
  plantilla.replace(/\{(\w+)\}/g, (_, k) => (k in vars ? String(vars[k]) : `{${k}}`));
const monto = (v) => `$${v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const minuscula = (s) => s.charAt(0).toLowerCase() + s.slice(1);

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const sumarDias = (fecha, dias) => { const d = new Date(fecha); d.setDate(d.getDate() + dias); return d; };
const sumarMeses = (fecha, meses) => new Date(fecha.getFullYear(), fecha.getMonth() + meses, Math.min(fecha.getDate(), 28));
const etiquetaLarga = (d) => `${d.getDate()} de ${MESES[d.getMonth()]}`;
const etiquetaCorta = (d) => `${d.getDate()} ${MESES_CORTOS[d.getMonth()]}`;
const etiquetaAnio = (d) => `${d.getDate()} ${MESES_CORTOS[d.getMonth()]} ${d.getFullYear()}`;
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
function proximoDia(desde, dia) {
  let d = new Date(desde.getFullYear(), desde.getMonth(), dia);
  if (d <= desde) d = new Date(desde.getFullYear(), desde.getMonth() + 1, dia);
  return d;
}

// Expresiones que se evalúan en la base al cargar (dependen de la fecha real).
const expr = (oracle, h2) => ({ expr: { oracle, h2 } });
const signo = (n) => (n >= 0 ? `+ ${n}` : `- ${-n}`);
const fechaRelativa = (dias) => expr(`TRUNC(SYSDATE) ${signo(dias)}`, `DATEADD(DAY, ${dias}, CURRENT_DATE)`);
const momentoRelativo = (dias, minutos = 0) => expr(
  `CAST(TRUNC(SYSDATE) ${signo(dias)} AS TIMESTAMP) + NUMTODSINTERVAL(${540 + minutos}, 'MINUTE')`,
  `DATEADD(MINUTE, ${540 + minutos}, CAST(DATEADD(DAY, ${dias}, CURRENT_DATE) AS TIMESTAMP))`);
const etiquetaRelativa = (dias, prefijo = '') => {
  const p = prefijo ? `'${prefijo.replace(/'/g, "''")}' || ` : '';
  return expr(
    `${p}TO_CHAR(TRUNC(SYSDATE) ${signo(dias)}, 'FMDD "de" month', 'NLS_DATE_LANGUAGE=SPANISH')`,
    `${p}FORMATDATETIME(DATEADD(DAY, ${dias}, CURRENT_DATE), 'd ''de'' MMMM', 'es')`);
};
const DIA_CORTE_CANDIDATO = expr(
  `LEAST(TO_NUMBER(TO_CHAR(TRUNC(SYSDATE) + ${DIAS_AVISO_PUSH}, 'DD')), 28)`,
  `LEAST(EXTRACT(DAY FROM DATEADD(DAY, ${DIAS_AVISO_PUSH}, CURRENT_DATE)), 28)`);

const filas = new Map();
const agregar = (tabla, fila) => {
  if (!filas.has(tabla)) filas.set(tabla, []);
  filas.get(tabla).push(fila);
  return fila;
};

// ---------------------------------------------------------------------------
// Parámetros, copy y catálogos
// ---------------------------------------------------------------------------
const PARAMETROS = [
  ['moneda.default', 'USD', 'Moneda de los montos del producto.'],
  ['sesion.horas', '12', 'Horas de vigencia de un token de sesión.'],
  ['fecha.meses_bloqueo', '6', 'Meses en que la fecha de cobro no se puede volver a cambiar después de un cambio.'],
  ['fecha.interes_base_dias', '365', 'Días del año para el interés de los días que se corre la cuota. Confirmar con la metodología del banco.'],
  ['fecha.bloqueada', 'Tu fecha de cobro se cambió hace poco. Se puede volver a cambiar desde el {fecha}.', 'Error al cambiar la fecha antes de que termine el bloqueo.'],
  ['fecha.interes_sin_aceptar', 'Antes de cambiarla necesitamos que aceptes el interés de los días que se corre tu cuota.', 'Error si se confirma por voz sin aceptar el interés.'],
  ['fecha.interes_sin_datos', 'No tenemos a mano el interés de ese cambio. En Telebanca te lo confirman antes de moverla.', 'Error si el crédito no trae saldo o tasa.'],
  ['fecha.costo.opcion_interes', 'Con este día tu cuota se corre {dias} días y lleva {monto} de interés, una sola vez.', 'Vista 04 y chat: interés del día elegido, antes de confirmar.'],
  ['fecha.costo.opcion_sin_interes', 'Con este día no hay interés extra.', 'Vista 04 y chat: el día no corre la cuota.'],
  ['fecha.costo.opcion_sin_datos', 'Con este día tu cuota se corre {dias} días. El interés te lo confirman en Telebanca.', 'Vista 04: el crédito no trae saldo o tasa.'],
  ['fecha.costo.listo_interes', 'Se cobra una sola vez, con tu cuota del {fecha}, por los {dias} días que se corre tu cobro. Después tu cuota vuelve a ser la de siempre.', 'Vista 05: interés aceptado.'],
  ['fecha.costo.listo_sin_interes', 'Este cambio no lleva interés. Tu cuota y tu plazo no cambian.', 'Vista 05: sin interés.'],
  ['fecha.costo.listo_sin_datos', 'Tu cuota se corre {dias} días. El interés te lo confirman en Telebanca.', 'Vista 05: sin saldo o tasa.'],
  ['fecha.pago_del_dia', '{pago} del {dia}', 'Cómo se nombra un pago con día fijo (voz).'],
  ['fecha.pago_fin_de_mes', '{pago} de fin de mes', 'Cómo se nombra un pago de fin de mes (voz).'],
  ['fecha.titulo_por_dias', 'DESPUÉS DE TU {pago}', 'Título del grupo de días calculados desde lo que dijo la persona.'],
  ['voz.banco', 'Bancoagrícola', 'Nombre del banco en la llamada.'],
  ['voz.saludo_manana', 'buenos días', 'Saludo antes del mediodía.'],
  ['voz.saludo_tarde', 'buenas tardes', 'Saludo desde el mediodía.'],
  ['voz.asistente', 'Lucía', 'Nombre de la asistente virtual. Siempre se presenta como asistente virtual.'],
  ['voz.proveedor', 'vapi', 'Proveedor de voz que hace las llamadas.'],
  ['voz.telebanca_hablado', '2 2 1 0, 0 0 0 0', 'Telebanca 2210-0000, 24 horas (bancoagricola.com/atencion-al-cliente), escrito para leerse cifra por cifra.'],
  ['voz.ventana_dias', 'lunes,martes,miercoles,jueves,viernes', 'LPC art. 18 lit. n: gestiones solo de lunes a viernes.'],
  ['voz.ventana_inicio', '08:00', 'LPC art. 18 lit. n: desde las 8:00.'],
  ['voz.ventana_fin', '18:00', 'LPC art. 18 lit. n: hasta las 18:00. La última llamada empieza voz.duracion_max_seg antes.'],
  ['voz.ventana_descripcion', 'Lunes a viernes, de 8:00 a 18:00 (LPC art. 18 lit. n).', 'Ventana de llamadas, para el tablero y n8n.'],
  ['voz.duracion_max_seg', '300', 'Duración máxima de la llamada. Igual a maxDurationSeconds del asistente.'],
  ['voz.categorias', 'A1', 'NCB-022: solo el tramo al día, el único con 0 % de reserva.'],
  ['voz.dias_min_antes_cobro', '15', 'La llamada alinea la fecha antes del día −15, fuera del tramo en que actúa la ruta.'],
  ['voz.max_intentos', '3', 'Intentos por cliente en voz.dias_ventana_intentos días. Debajo del tope de 7 en 7 de la Regulation F (informe 03).'],
  ['voz.dias_ventana_intentos', '7', 'Ventana en que se cuentan los intentos.'],
  ['voz.dias_entre_intentos', '1', 'Como mucho un intento por día.'],
  ['voz.max_opciones', '2', 'Fechas que se ofrecen por voz: más de dos no se retienen al oído.'],
  ['voz.fuera_de_ventana', 'Fuera del horario permitido para llamar: lunes a viernes de 8:00 a 18:00.', 'Error al programar fuera de la ventana legal.'],
  ['voz.no_llamable', 'A este cliente no se le llama ahora: {motivo}.', 'Error al programar a alguien que no entra.'],
  ['voz.ingreso.salario', 'salario', 'Cómo se nombra el ingreso en voz.'],
  ['voz.ingreso.pension', 'pensión', 'Cómo se nombra el ingreso en voz.'],
  ['voz.ingreso.remesa', 'remesa', 'Cómo se nombra el ingreso en voz.'],
  ['voz.ingreso.negocio', 'ingreso del negocio', 'Cómo se nombra el ingreso en voz.'],
  ['voz.ingreso.otro', 'pago', 'Cómo se nombra el ingreso en voz.'],
  ['voz.opcion', 'el día {dia} de cada mes, {margen} días después de tu {pago}. El primer cobro con esa fecha sería el {desde}.', 'Una fecha ofrecida por voz.'],
  ['voz.opcion_semanal', 'el día {dia} de cada mes, cuando ya recibiste tu pago de la semana. El primer cobro con esa fecha sería el {desde}.', 'Una fecha ofrecida por voz a quien cobra cada semana.'],
  ['voz.opcion_variable', 'el día {dia} de cada mes, que es cuando más te suele llegar dinero según los últimos tres meses. El primer cobro con esa fecha sería el {desde}.', 'Una fecha ofrecida por voz con ingreso variable, sin días dichos.'],
  ['voz.opcion_sin_interes', 'Tu cuota no cambia.', 'Cuando el cambio no corre la cuota.'],
  ['voz.opcion_interes', 'Como esa cuota se corre {dias} días, solo esa cuota lleva {monto} de interés. Después vuelve a ser la de siempre.', 'Interés de los días corridos, una sola vez.'],
  ['voz.opcion_interes_sin_datos', 'Esa cuota se corre {dias} días y lleva un interés que te confirman en Telebanca.', 'Cuando el crédito no trae saldo o tasa.'],
  ['voz.propuesta_una', 'Tu cobro quedaría {opcion}', 'Propuesta con una fecha.'],
  ['voz.propuesta_dos', 'Tengo dos opciones. La primera, {a} La segunda, {b}', 'Propuesta con dos fechas.'],
  ['voz.sin_opciones', 'Con esos días no encuentro una fecha que te deje margen. En Telebanca, al {telebanca}, lo revisan contigo.', 'Sin fechas posibles.'],
  ['voz.bloqueado', 'Tu fecha de cobro se cambió hace poco, así que se puede volver a cambiar desde el {hasta}. Si lo necesitas antes, tu asesor o Telebanca, al {telebanca}, te ayudan.', 'Cambio bloqueado.'],
  ['voz.confirmado', 'Listo, tu cobro queda el día {dia} de cada mes, desde el {desde}.', 'Confirmación por voz.'],
  ['voz.confirmado_interes', 'Esa primera cuota lleva {monto} de interés, una sola vez.', 'Confirmación del interés.'],
  ['voz.confirmado_bloqueo', 'Esta fecha se puede volver a cambiar dentro de {meses} meses. Antes de eso, con tu asesor o en Telebanca, al {telebanca}.', 'Aviso del bloqueo al confirmar.'],
  ['voz.agencia', 'la agencia {nombre}, en {direccion}. Atiende {horario}', 'Agencia más cercana, para leer en voz.'],
  ['voz.sin_agencia', 'Telebanca, al {telebanca}, que atiende las 24 horas, te dice qué agencia te queda más cerca', 'Cuando no hay agencia registrada cerca.'],
  ['fecha.frecuencia_default', 'freq-quincena-finmes', 'Frecuencia de pago asumida si el cliente no eligió una.'],
  ['fecha.dias_excluidos', '28,29,30,31', 'Días que nunca se ofrecen como nueva fecha de cobro.'],
  ['fecha.label_dia', 'Día {dia}', 'Etiqueta de una opción de fecha.'],
  ['fecha.desde', 'desde el {fecha}', 'Desde cuándo aplica la nueva fecha de cobro.'],
  ['fecha.semanal_desde', 'desde la próxima semana', 'Desde cuándo aplica una fecha semanal.'],
  ['fecha.min_dias_entre_cobros', '14', 'Días mínimos entre el cobro actual y el primero con la fecha nueva (evita dos cobros seguidos).'],
  ['fecha.nota', 'El {dia} ya llevas {dias} días con tu {pago} en la cuenta.', 'Nota de cada día ofrecido en ¿Qué día te queda mejor?'],
  ['fecha.nota_variable', 'Según lo que te llegó en los últimos tres meses.', 'Nota de los días sugeridos con ingreso variable.'],
  ['fecha.titulo_variable', 'CUANDO SUELE ENTRAR MÁS DINERO', 'Título del grupo de días con ingreso variable.'],
  ['fecha.pago_variable', 'ingreso', 'Cómo llamar al pago con ingreso variable.'],
  ['fecha.semanal_dia_pago', 'viernes', 'Día de la semana en que se asume el pago semanal.'],
  ['fecha.sugerencia', 'El {dia} te deja {dias} días de margen desde tu {pago}: es el que mejor cuida tu saldo.', 'Motivo de la sugerencia de fecha.'],
  ['frecuencia.sugerencia', 'Tus abonos suelen entrar {patron}: por eso te sugerimos «{frecuencia}».', 'Motivo de la sugerencia de frecuencia.'],
  ['apartado.nota', 'Apartar no es pagar antes: el dinero se congela en tu cuenta y sigue siendo tuyo.', 'Nota de ¿En cuántas partes?'],
  ['apartado.se_paga', 'se paga completo el {fecha}', 'Cuándo se paga lo apartado.'],
  ['apartado.primera_cuota', 'primera cuota completa el {fecha}', 'Primera cuota cubierta por la ruta.'],
  ['apartado.min_dias_para_cobro', '21', 'Días mínimos hasta el cobro para armar la ruta en ese ciclo. Si faltan menos, se arma para el siguiente.'],
  ['apartado.calendario.aparta', 'Apartamos la {n} parte', 'Fila del calendario de partes.'],
  ['apartado.calendario.aparta_una', 'Apartamos tu cuota completa', 'Fila del calendario con una sola parte.'],
  ['apartado.calendario.paga', 'Se paga tu cuota completa', 'Última fila del calendario.'],
  ['apartado.ordinales', 'primera,segunda,tercera,cuarta', 'Ordinales de las partes.'],
  ['apartado.credito.nota.personal', 'cuota del mes', 'Nota del crédito apartable.'],
  ['apartado.credito.nota.card', 'pago de contado', 'Nota de la tarjeta apartable.'],
  ['apartado.origen.titulo', 'Desde tu cuenta de {tipo}', 'Título de la cuenta origen.'],
  ['apartado.origen.nombre', 'Cuenta {numero}', 'Nombre de la cuenta origen.'],
  ['apartado.origen.detalle', 'De aquí se aparta y de aquí se paga.', 'Detalle de la cuenta origen.'],
  ['apartado.paso.varias', 'El {dias} apartamos {monto}', 'Paso 1 con varias partes.'],
  ['apartado.paso.una', 'El {dias} apartamos tu cuota completa ({monto})', 'Paso 1 con una sola parte.'],
  ['apartado.paso.paga', 'El {dia} pagamos tu cuota completa', 'Paso 2.'],
  ['apartado.paso.aviso', 'Te avisamos cada vez, al momento', 'Paso 3.'],
  ['apartado.partes_invalidas', 'Con tu forma de pago, la cuota se aparta en {permitidas}.', 'Error si piden un número de partes fuera de lo permitido.'],
  ['apartado.sin_cuenta', 'Para apartar necesitamos una cuenta tuya en el banco: de ahí se congela y de ahí se paga.', 'Error al apartar sin cuenta propia.'],
  ['apartado.sugerencia', '{partes} te acomodan mejor: coinciden con tus pagos y ninguna parte aprieta.', 'Motivo de la sugerencia de partes.'],
  ['resumen.fila.cobro', 'Tu cobro', 'Etiqueta del resumen.'],
  ['resumen.fila.cobro_valor', 'Día {dia} de cada mes', 'Valor del resumen.'],
  ['resumen.fila.apartamos', 'Apartamos', 'Etiqueta del resumen.'],
  ['resumen.fila.apartamos_valor', '{monto} el {dias}', 'Valor del resumen.'],
  ['resumen.fila.desde', 'Desde', 'Etiqueta del resumen.'],
  ['resumen.fila.desde_valor', '{tipo} {numero}', 'Valor del resumen.'],
  ['resumen.fila.primera', 'Primera cuota completa', 'Etiqueta del resumen.'],
  ['resumen.fila.automatico', 'En automático', 'Etiqueta del resumen.'],
  ['resumen.fila.automatico_si', 'Sí, te avisamos cada vez', 'Valor del resumen.'],
  ['resumen.fila.automatico_no', 'No, tú apartas cada parte', 'Valor del resumen.'],
  ['cuenta.tipo.savings', 'ahorro', 'Nombre corto del tipo de cuenta.'],
  ['cuenta.tipo.debit', 'débito', 'Nombre corto del tipo de cuenta.'],
  ['cuenta.contrato.titular', 'Titular', 'Fila del contrato.'],
  ['cuenta.contrato.cuenta', 'Cuenta', 'Fila del contrato.'],
  ['cuenta.contrato.apertura', 'Costo de apertura', 'Fila del contrato.'],
  ['cuenta.contrato.mensual', 'Costo mensual', 'Fila del contrato.'],
  ['cuenta.contrato.para', 'Para qué', 'Fila del contrato.'],
  ['cuenta.contrato.para_valor', 'Apartar tu cuota', 'Fila del contrato.'],
  ['inicio.cuenta.producto', 'Cuenta de {tipo} · {producto}', 'Título de la tarjeta de saldo.'],
  ['inicio.credito.detalle', 'Cuota · se cobra el {dia}', 'Detalle de un crédito con cuota.'],
  ['inicio.credito.detalle_cambio', 'Cuota · el {dia} desde {mes}', 'Detalle de un crédito con fecha cambiada.'],
  ['inicio.tarjeta.detalle', 'Disponible de {limite}', 'Detalle de la tarjeta.'],
  ['inicio.ruta.aparta', 'Aparta {monto}', 'Hito de Mi ruta.'],
  ['inicio.ruta.paga', 'Se paga {monto}', 'Hito de Mi ruta.'],
  ['producto.cambiar_fecha.detalle', 'Que tu cuota caiga cuando ya te pagaron', 'Detalle del producto en el inicio.'],
  ['producto.deposito.detalle', 'Crece a tasa fija', 'Detalle del producto en el inicio.'],
  ['producto.deposito.condiciones', 'Desde $100 · Tasa fija del 5.25 % anual · Plazo de 6 a 12 meses · Sin comisiones', 'Lo que se muestra antes de activar el depósito.'],
  ['aviso.fecha.titulo', 'Tu cobro ahora es el día {dia}', 'Aviso al cambiar la fecha.'],
  ['aviso.fecha.cuerpo_interes', 'Aplica desde el {fecha}. Tu cuota y tu plazo no cambian. Esa primera cuota lleva {monto} de interés por {dias} días, una sola vez.', 'Aviso al cambiar la fecha con interés aceptado.'],
  ['aviso.fecha.cuerpo', 'Aplica desde el {fecha}. El monto y el plazo de tu crédito no cambian.', 'Aviso al cambiar la fecha.'],
  ['aviso.ruta.titulo', 'Tu ruta quedó lista', 'Aviso al activar el apartado.'],
  ['aviso.ruta.cuerpo', 'Apartamos {monto} el {dias} y el {dia} tu cuota se paga sola.', 'Aviso al activar el apartado.'],
  ['aviso.parte.titulo', 'Llevas {n} de {total} partes', 'Aviso al apartar una parte intermedia.'],
  ['aviso.parte.mitad', 'Ya va la mitad', 'Aviso al apartar la mitad.'],
  ['aviso.parte.cuerpo', 'Apartamos {monto} de tu pago. Tu cuota del {fecha} va {progreso}.', 'Aviso al apartar una parte.'],
  ['aviso.completa.titulo', 'Tu cuota ya está completa', 'Aviso al apartar la última parte.'],
  ['aviso.completa.cuerpo', 'Apartamos los últimos {monto}. El {dia} se paga sola. Tú no tienes que hacer nada.', 'Aviso al apartar la última parte.'],
  ['aviso.pagado.titulo', 'Pagado, y a tiempo', 'Aviso al pagar la cuota.'],
  ['aviso.pagado.cuerpo', 'Tu cuota de {monto} se pagó sola. Ya son {meses} meses seguidos: mira tu récord.', 'Aviso al pagar la cuota.'],
  ['aviso.choque.titulo', 'Este mes vino distinto, y está bien', 'Aviso cuando una parte no alcanzó.'],
  ['aviso.choque.cuerpo', 'No alcanzó para apartar los {monto} de tu {pago}. Toca aquí y tu asesor te ayuda a ajustarlo. Sin costo.', 'Aviso cuando una parte no alcanzó.'],
  ['aviso.choque.evento', 'La parte del {fecha} no alcanzó', 'Evento del contexto de choque.'],
  ['aviso.cuenta.titulo', 'Tu cuenta quedó abierta', 'Aviso al abrir la cuenta.'],
  ['aviso.cuenta.cuerpo', '{producto} {numero}. Sin costo y sin saldo mínimo: de aquí se aparta y de aquí se paga.', 'Aviso al abrir la cuenta.'],
  ['aviso.deposito.titulo', 'Tu depósito a plazo quedó activo', 'Aviso al activar el depósito.'],
  ['aviso.deposito.cuerpo', 'Crece a tasa fija y te avisamos cuando venza. Lo ves en tus productos.', 'Aviso al activar el depósito.'],
  ['aviso.cita.titulo', 'Tu asistencia quedó agendada', 'Aviso al agendar.'],
  ['aviso.cita.cuerpo', '{cuando} con {asesora} en {donde}. Ya sabe que es sobre tu {tema}.', 'Aviso al agendar.'],
  ['aviso.acuerdo.titulo', 'Guardamos lo que acordamos', 'Aviso al cerrar un acuerdo en el chat.'],
  ['aviso.acuerdo.cuerpo', '{detalle} Lo ves en tu inicio.', 'Aviso al cerrar un acuerdo en el chat.'],
  ['record.sumando.fecha', 'Cambiaste tu fecha de cobro', 'Lo que suma al récord.'],
  ['record.sumando.apartas', 'Apartas tu cuota en partes', 'Lo que suma al récord.'],
  ['record.sumando.pagado', 'Pagaste {mes} a tiempo', 'Lo que suma al récord.'],
  ['record.explicacion', 'Cada cuota a tiempo suma un mes a tu récord. Lo que no fue a tiempo sale solo, en su fecha.', 'Explicación del camino a 24 de 24.'],
  ['record.explicacion.salida', 'Lo que no fue a tiempo sale de tu récord en su fecha ({fechas}). Mientras, cada cuota a tiempo suma.', 'Explicación cuando hay salidas.'],
  ['record.suma.detalle.apartas', 'Tu cuota queda completa antes de la fecha. El dinero se congela en tu cuenta y se paga entero ese día.', 'Detalle de lo que suma.'],
  ['record.suma.detalle.linea', 'Usas el {pct} % de tu línea. Por debajo del 50 %: tienes espacio y lo cuidas.', 'Detalle de lo que suma.'],
  ['record.suma.detalle.generico', 'Cada mes a tiempo se suma a tu historial con nosotros.', 'Detalle de lo que suma.'],
  ['record.proximo', 'Tu próximo +1: {fecha}', 'Etiqueta del próximo +1.'],
  ['resumen.personal', 'va al día: tu cuota es de {monto}', 'Resumen de crédito personal al día.'],
  ['resumen.card', 'va al día: tu pago de contado es de {monto}', 'Resumen de tarjeta al día.'],
  ['resumen.cuenta', 'tiene {monto} disponibles', 'Resumen de una cuenta.'],
  ['resumen.parte_pendiente', 'tiene una parte de {monto} por acomodar, y tiene arreglo', 'Resumen cuando una parte quedó corta.'],
  ['resumen.otro', 'está en orden', 'Resumen sin producto específico.'],
  ['cita.cuando', '{dia} · {hora}', 'Cuándo es la asistencia.'],
  ['cita.donde', 'Agencia {agencia}', 'Dónde es la asistencia.'],
  ['cita.sobre', 'tu {tema}', 'Sobre qué es la asistencia.'],
  ['cita.nota', '{asesora} ya sabe que es sobre tu {tema}, así llegas y van al grano.', 'Confirmación de la asistencia.'],
  ['apertura.debe_aceptar', 'Para abrir tu cuenta necesitamos que aceptes las condiciones.', 'Mensaje si no se aceptan las condiciones.'],
  ['demo.deposito_apertura', 'cust-mauricio-sosa=640.00,cust-samuel-quijada=420.00,cust-gabriela-romero=560.00,cust-fatima-argueta=380.00,cust-rene-aguilar=120.00',
    'Demo: su primer ingreso ya acreditado al abrir la cuenta, para que quien no tenía cuenta tenga saldo que apartar. Ilustrativo: vacío en producción.'],
  ['chat.max_turnos', '30', 'Turnos del cliente antes de escalar a una persona (MAX_MESSAGES_BEFORE_ESCALATION).'],
  ['chat.historial_max', '12', 'Mensajes recientes que se envían como contexto al modelo.'],
  ['chat.tiers_asesora', 'platino,black', 'Tarjetas que escalan a su asesora nombrada. El resto va al Centro de Atención.'],
  ['chat.rechazos.diligente', '2', 'Rechazos seguidos antes de escalar.'],
  ['chat.rechazos.olvidadizo', '2', 'Rechazos seguidos antes de escalar.'],
  ['chat.rechazos.resistente', '1', 'Un solo intento: insistirle no es cuidado, es ruido.'],
  ['chat.rechazos.despreocupado', '2', 'Rechazos seguidos antes de escalar.'],
  ['chat.orden.diligente', 'qr-fecha,qr-apartar,qr-automatico,qr-asesora,qr-lo-pienso', 'Orden de ofertas (tono), nunca cambia monto ni plazo.'],
  ['chat.orden.olvidadizo', 'qr-automatico,qr-apartar,qr-fecha,qr-asesora,qr-lo-pienso', 'Orden de ofertas (tono), nunca cambia monto ni plazo.'],
  ['chat.orden.resistente', 'qr-apartar,qr-fecha,qr-asesora', 'Orden de ofertas (tono), nunca cambia monto ni plazo.'],
  ['chat.orden.despreocupado', 'qr-fecha,qr-apartar,qr-automatico,qr-asesora,qr-lo-pienso', 'Orden de ofertas (tono), nunca cambia monto ni plazo.'],
  ['chat.tono.diligente', 'Buen historial y primera vez cerca de un tropiezo: tono ligero y confiado.', 'Guía de tono para el modelo.'],
  ['chat.tono.olvidadizo', 'El problema es la memoria, no la voluntad: sugiere dejarlo en automático.', 'Guía de tono para el modelo.'],
  ['chat.tono.resistente', 'Breve y directo, un solo intento, sin insistir.', 'Guía de tono para el modelo.'],
  ['chat.tono.despreocupado', 'Nombra lo que le suma en su récord al resolverlo, sin volverlo castigo.', 'Guía de tono para el modelo.'],
  ['chat.tranquilidad', 'Le pasa a cualquiera, y tiene arreglo.', 'Frase de tranquilidad por defecto.'],
  ['chat.apertura.general', 'Hola, {nombre}. Estoy aquí para ayudarte con tus pagos y tu crédito. La mayoría lo resuelve moviendo la fecha de cobro o apartando la cuota en partes. ¿Qué te gustaría hacer?', 'Primer mensaje sin evento de choque.'],
  ['chat.apertura.shock.diligente', '{nombre}, vi que {evento}. {tranquilidad} La mayoría de clientes como tú lo resuelve moviendo la fecha de cobro o apartando la cuota en partes. ¿Cuál te acomoda?', 'Apertura tras un choque.'],
  ['chat.apertura.shock.olvidadizo', '{nombre}, vi que {evento}. {tranquilidad} Si quieres, lo dejamos en automático para que no vuelva a pasar. ¿Lo vemos?', 'Apertura tras un choque.'],
  ['chat.apertura.shock.resistente', '{nombre}, vi que {evento}. Tengo dos formas de resolverlo ahora mismo: apartar la cuota en partes o mover la fecha de cobro.', 'Apertura tras un choque.'],
  ['chat.apertura.shock.despreocupado', '{nombre}, vi que {evento}. Resolverlo ahora te sigue sumando en tu récord, y tiene arreglo fácil. ¿Movemos la fecha o apartamos en partes?', 'Apertura tras un choque.'],
  ['chat.ofertas', 'La mayoría de clientes como tú lo resuelve con alguna de estas opciones, {nombre}. ¿Cuál te acomoda?', 'Ofertas concretas.'],
  ['chat.pregunta.interes', '{costo} ¿La cambio al día {dia}, {nombre}?', 'Chat: el día elegido corre la cuota. Se pregunta antes de mover la fecha.'],
  ['chat.costo.interes', 'Esa primera cuota lleva {monto} de interés, una sola vez.', 'Chat: interés aceptado.'],
  ['chat.costo.sin_interes', 'Tu cuota y tu plazo no cambian.', 'Chat: sin interés.'],
  ['chat.acuerdo.fecha', 'Listo, {nombre}. Tu cobro ahora es el día {dia}, desde el {fecha}. {costo} Ya quedó guardado en tu inicio.', 'Acuerdo: cambiar fecha (ejecutado en el mismo turno).'],
  ['chat.acuerdo.apartar', 'Perfecto, {nombre}. Apartamos {monto} en {partes} y se paga completo el {fecha}. Apartar no es pagar antes: el dinero sigue siendo tuyo. Tu ruta ya quedó activa.', 'Acuerdo: apartar (ejecutado en el mismo turno).'],
  ['chat.acuerdo.automatico', 'Hecho, {nombre}. Lo dejamos en automático desde tu cuenta {cuenta}: de ahí se aparta y de ahí se paga el {fecha}. Puedes quitarlo cuando quieras.', 'Acuerdo: automático.'],
  ['chat.pregunta.frecuencia', 'Para acomodarlo a tus pagos, {nombre}: ¿qué día te pagan?', 'El chat pregunta la frecuencia de pago antes de mover la fecha o apartar.'],
  ['chat.pregunta.dia', 'Estos días caen después de que te pagan, {nombre}. Si alguno corre tu cuota, te digo el interés antes de cambiarla. ¿Cuál te queda mejor?', 'El chat ofrece los días válidos.'],
  ['chat.pregunta.partes', '¿En cuántas partes la apartamos, {nombre}? {nota}', 'El chat ofrece las partes permitidas.'],
  ['chat.sin_cuenta', 'Para apartar necesitamos una cuenta tuya con nosotros, {nombre}: de ahí se congela y de ahí se paga. La abres en dos minutos desde «Apartar mi cuota» en tu inicio, sin costo.', 'Apartar sin cuenta propia.'],
  ['chat.sin_credito', 'No veo un crédito con cuota en tus productos, {nombre}. Si quieres, lo revisamos con {asesora}.', 'Sin crédito apartable.'],
  ['chat.ya_tiene_ruta', 'Tu ruta ya está activa, {nombre}: apartamos {monto} en {partes} y se paga el {fecha}. ¿Quieres cambiar algo?', 'Ya existe un apartado activo.'],
  ['chat.fecha_invalida', 'Ese día no cae después de tu pago, {nombre}. Te dejo los que sí:', 'El día pedido no está en el set permitido.'],
  ['chat.pregunta.libre', 'Con gusto, {nombre}. {respuesta}', 'Prefijo para respuestas libres del modelo.'],
  ['chat.siguiente_paso', 'Claro, {nombre}, piénsalo con calma. Tus opciones quedan a mano en tu inicio: cuando quieras, tocas «Apartar mi cuota» o «Cambiar fecha de cobro» y lo resuelves en tres toques.', 'Cierre con siguiente paso concreto.'],
  ['chat.rechazo.suave', 'Sin problema, {nombre}. ¿Te sirve alguna de estas otras opciones?', 'Tras un rechazo.'],
  ['chat.rechazo.ultimo', 'Entiendo, {nombre}. Antes de pasarte con una persona, ¿alguna de estas te sirve?', 'Último intento antes de escalar.'],
  ['chat.escalar.asesora', 'Entiendo, {nombre}. Esto lo vemos mejor con {asesora}, sin costo. ¿Agendamos una asistencia?', 'Escalamiento a la asesora nombrada.'],
  ['chat.escalar.centro', 'Entiendo, {nombre}. Lo vemos mejor con el Centro de Atención al Cliente, con calma y sin costo. ¿Quieres que te contacten?', 'Escalamiento al Centro de Atención.'],
  ['chat.escalar.turnos', 'Llevamos un buen rato, {nombre}, y quiero que esto quede resuelto.', 'Prefijo al escalar por límite de turnos.'],
  ['chat.cita.agendada', 'Listo, {nombre}: {cuando} con {asesora} en {donde}. Ya sabe que es sobre tu {tema}.', 'Confirmación de asistencia desde el chat.'],
  ['chat.centro.confirmado', 'Listo, {nombre}. El Centro de Atención al Cliente te contacta en horario hábil para revisar tu caso con calma.', 'Confirmación de contacto del Centro de Atención.'],
  ['chat.negativa', 'Está bien, {nombre}, no insisto. Si más adelante quieres verlo, aquí estoy.', 'Cierre ante una negativa explícita.'],
  ['chat.redireccion', 'Puedo ayudarte con tu crédito y tus pagos, {nombre}. ¿Seguimos con eso? La mayoría lo resuelve moviendo la fecha de cobro o apartando la cuota en partes.', 'Redirección ante temas ajenos.'],
  ['chat.jailbreak', 'Mi tarea es ayudarte con tus pagos y tu crédito, {nombre}, y ahí me quedo. ¿Seguimos con lo tuyo?', 'Respuesta ante intentos de cambiar las reglas.'],
  ['chat.cerrado', 'Tu conversación quedó resuelta, {nombre}. Si necesitas algo más, cuéntame y lo vemos.', 'Mensaje en una conversación ya cerrada.'],
  ['form.razon_heuristica', 'Te sugerimos esta opción como punto de partida. Tú decides.', 'Motivo cuando la sugerencia no viene de un modelo.'],
  ['form.sin_opciones', 'No hay opciones para recomendar.', 'Respuesta si el formulario no trae opciones.'],
  ['push.tipo', 'corte-cercano', 'Tipo del aviso preventivo.'],
  ['push.titulo', 'Tu corte se acerca y hay opciones', 'Título del push preventivo.'],
  ['push.cuerpo', '{nombre}, faltan {dias} días para tu corte. La mayoría lo resuelve moviendo la fecha o repartiendo la cuota. Toca para verlo.', 'Cuerpo del push preventivo.'],
  ['push.cuerpo_un_dia', '{nombre}, falta 1 día para tu corte. La mayoría lo resuelve moviendo la fecha o repartiendo la cuota. Toca para verlo.', 'Cuerpo del push a un día del corte.'],
];
const COPY = Object.fromEntries(PARAMETROS.map(([k, v]) => [k, v]));
PARAMETROS.forEach(([clave, valor, descripcion]) => agregar('PARAMETRO_APP', { clave, valor, descripcion }));

for (let dia = 1; dia <= 28; dia++) {
  const descripcion = dia === 7 ? 'Corte primera quincena del mes'
    : dia === 21 ? 'Corte segunda quincena del mes'
      : `Corte día ${dia}`;
  agregar('PARAMETRO_CORTE', { dia_corte: dia, descripcion, activo: true });
}

// partes_permitidas sigue la lógica de negocio: quien cobra una vez al mes no
// puede repartir en dos; quien cobra por semana sí puede repartir en cuatro.
[
  { id: 'freq-quincena-finmes', slug: 'quincena-fin-de-mes', label: 'Quincena y fin de mes', descripcion: null, tipo: 'mensual', offset_min: 3, offset_max: 4, partes_permitidas: '2,3,4', partes_sugeridas: 2, nota_partes: 'Dos partes coinciden con tu quincena y tu fin de mes.', orden: 1 },
  { id: 'freq-solo-finmes', slug: 'fin-de-mes', label: 'Solo fin de mes', descripcion: null, tipo: 'mensual', offset_min: 3, offset_max: 4, partes_permitidas: '1', partes_sugeridas: 1, nota_partes: 'Te pagan una vez al mes: apartamos tu cuota completa justo después de tu pago.', orden: 2 },
  { id: 'freq-semanal', slug: 'semanal', label: 'Cada semana', descripcion: null, tipo: 'semanal', offset_min: 3, offset_max: 4, partes_permitidas: '2,3,4', partes_sugeridas: 4, nota_partes: 'Cuatro partes coinciden con tus pagos semanales y cada una es pequeña.', orden: 3 },
  { id: 'freq-variable', slug: 'variable', label: 'Es variable', descripcion: 'Tomamos el promedio de los últimos 3 meses', tipo: 'variable', offset_min: 3, offset_max: 3, partes_permitidas: '2,3', partes_sugeridas: 2, nota_partes: 'Dos partes siguen el promedio de lo que te llegó en los últimos tres meses.', orden: 4 },
].forEach((f) => agregar('CATALOGO_FRECUENCIA', { ...f, activo: true }));

[
  { frecuencia_id: 'freq-quincena-finmes', idx: 1, dia_pago: 15, fin_de_mes: false, dia_semana: null, opcion_id: null, label: null, hint: 'tras el 15', titulo: 'DESPUÉS DE TU QUINCENA DEL 15', pago_label: 'quincena' },
  { frecuencia_id: 'freq-quincena-finmes', idx: 2, dia_pago: null, fin_de_mes: true, dia_semana: null, opcion_id: null, label: null, hint: 'tras fin de mes', titulo: 'DESPUÉS DE TU PAGO DE FIN DE MES', pago_label: 'pago de fin de mes' },
  { frecuencia_id: 'freq-solo-finmes', idx: 1, dia_pago: null, fin_de_mes: true, dia_semana: null, opcion_id: null, label: null, hint: 'tras fin de mes', titulo: 'DESPUÉS DE TU PAGO DE FIN DE MES', pago_label: 'pago de fin de mes' },
  { frecuencia_id: 'freq-semanal', idx: 1, dia_pago: null, fin_de_mes: false, dia_semana: 'viernes', opcion_id: 'date-vie', label: 'Los viernes', hint: null, titulo: 'DESPUÉS DE TU PAGO DE CADA VIERNES', pago_label: 'pago semanal' },
  { frecuencia_id: 'freq-variable', idx: 1, dia_pago: 15, fin_de_mes: false, dia_semana: null, opcion_id: null, label: null, hint: null, titulo: 'CUANDO SUELE ENTRAR MÁS DINERO', pago_label: 'ingreso' },
].forEach((d) => agregar('CATALOGO_FRECUENCIA_DIA', d));

[1, 2, 3, 4].forEach((parts, i) => agregar('CATALOGO_PARTES', { parts, label: parts === 1 ? '1 parte' : `${parts} partes`, orden: i + 1, activo: true }));

const DIAS_ASESORIA = [
  ['day-manana', 'Mañana', 'Mañana'],
  ['day-miercoles', 'Miércoles', 'Este miércoles'],
  ['day-jueves', 'Jueves', 'Este jueves'],
  ['day-viernes', 'Viernes', 'Este viernes'],
];
DIAS_ASESORIA.forEach(([id, label, when_label], i) => agregar('CATALOGO_DIA_ASESORIA', { id, label, when_label, orden: i + 1, activo: true }));

const HORAS_ASESORIA = [['time-0900', '9:00 a. m.'], ['time-1030', '10:30 a. m.'], ['time-1400', '2:00 p. m.'], ['time-1530', '3:30 p. m.']];
HORAS_ASESORIA.forEach(([id, label], i) => agregar('CATALOGO_HORA_ASESORIA', { id, label, orden: i + 1, activo: true }));

const OPCIONES_CHAT = [
  ['qr-fecha', 'Cambiar mi fecha de cobro', 'fecha', 'oferta'],
  ['qr-apartar', 'Apartar mi cuota en partes', 'apartar', 'oferta'],
  ['qr-automatico', 'Dejarlo en automático', 'automatico', 'oferta'],
  ['qr-asesora', 'Hablar con una persona', 'asesora', 'oferta'],
  ['qr-lo-pienso', 'Lo pienso y lo veo después', 'seguir', 'oferta'],
  ['qr-agendar', 'Sí, por favor', 'agendar', 'escalamiento'],
  ['qr-no-gracias', 'No, gracias', 'no_gracias', 'escalamiento'],
];
const ETIQUETA_OPCION = Object.fromEntries(OPCIONES_CHAT.map(([id, label]) => [id, label]));
OPCIONES_CHAT.forEach(([id, label, accion, contexto], i) =>
  agregar('CATALOGO_CHAT_OPCION', { id, label, accion, contexto, orden: i + 1, activo: true }));

agregar('OFERTA', { id: 'offer-change-date', okey: 'change-date', title: 'Cambiar fecha de cobro', subtitle: 'Que tu cuota caiga cuando ya te pagaron', highlighted: true, orden: 1, activo: true });
agregar('OFERTA', { id: 'offer-term-deposit', okey: 'term-deposit', title: 'Depósito a plazo digital', subtitle: 'Crece a tasa fija', highlighted: false, orden: 2, activo: true });

agregar('OFERTA_APERTURA', { id: 'apertura-debito', product_name: 'Cuenta de débito Max Electrónico', account_product_name: 'Max Electrónico', account_tipo: 'debit', opening_cost: 0, monthly_cost: 0, activo: true });
['No es un crédito.', 'Sin costo de apertura ni manejo mensual.', 'Recibe tu sueldo y de aquí se aparta y se paga.']
  .forEach((texto, i) => agregar('OFERTA_APERTURA_CONDICION', { oferta_id: 'apertura-debito', idx: i + 1, texto }));
agregar('OFERTA_APERTURA_DOCUMENTO', { id: 'doc-contrato', oferta_id: 'apertura-debito', titulo: 'Contrato de cuenta', url: 'https://example.com/contrato', orden: 1 });
agregar('OFERTA_APERTURA_DOCUMENTO', { id: 'doc-tarifario', oferta_id: 'apertura-debito', titulo: 'Tarifario', url: 'https://example.com/tarifario', orden: 2 });

const ASESORES = [
  { id: 'adv-andrea', name: 'Andrea Portillo', since_label: 'marzo de 2024', agency: 'Metrocentro', initials: 'AP', avatar_color: '#00714E' },
  { id: 'adv-roberto', name: 'Roberto Menjívar', since_label: 'enero de 2022', agency: 'Multiplaza', initials: 'RM', avatar_color: '#1F5FA8' },
  { id: 'adv-karla', name: 'Karla Alvarado', since_label: 'junio de 2023', agency: 'Santa Ana Centro', initials: 'KA', avatar_color: '#8A3FFC' },
  { id: 'adv-jose', name: 'José Rivas', since_label: 'agosto de 2021', agency: 'San Miguel', initials: 'JR', avatar_color: '#B35C00' },
  { id: 'adv-gabriela', name: 'Gabriela Chávez', since_label: 'noviembre de 2024', agency: 'Soyapango', initials: 'GC', avatar_color: '#007A87' },
  { id: 'adv-daniel', name: 'Daniel Quintanilla', since_label: 'febrero de 2020', agency: 'Escalón', initials: 'DQ', avatar_color: '#5A6B7B' },
  { id: 'adv-sofia', name: 'Sofía Martínez', since_label: 'abril de 2025', agency: 'La Libertad', initials: 'SM', avatar_color: '#A3316F' },
  { id: 'adv-mario', name: 'Mario Aguilar', since_label: 'septiembre de 2019', agency: 'Plaza Mundo', initials: 'MA', avatar_color: '#3E7D1F' },
];
ASESORES.forEach((a) => agregar('ASESOR', { ...a, activo: true }));

[
  ['topic-personal', 'personal', 'Crédito personal'],
  ['topic-card', 'card', 'Tarjeta de crédito'],
  ['topic-savings', 'cuenta', 'Cuenta {producto}'],
  ['topic-other', 'other', 'Otro tema'],
].forEach(([id, producto_tipo, label], i) => agregar('ASESORIA_TEMA', { id, producto_tipo, label, orden: i + 1, activo: true }));

const FRECUENCIA_POR_ARQUETIPO = { diligente: 'freq-quincena-finmes', olvidadizo: 'freq-solo-finmes', despreocupado: 'freq-variable', resistente: 'freq-semanal' };

// ---------------------------------------------------------------------------
// Clientes: 12 impecables, 15 mejorables, 15 fatales
// ---------------------------------------------------------------------------
const CLIENTES = [
  { nombre: 'Edgar', apellido: 'Gómez', perfil: 'impecable', cat: 'A1', tier: 'platino', arq: 'diligente', asesor: 0, id: 'cust-edgar', color: '#7E4FBC', edgar: true },
  { nombre: 'Lucía', apellido: 'Hernández', perfil: 'impecable', cat: 'A1', tier: 'black', arq: 'diligente', asesor: 1, legado: 'lucia' },
  { nombre: 'Carlos', apellido: 'Ramírez', perfil: 'impecable', cat: 'A2', tier: 'oro', arq: 'olvidadizo', asesor: 2, legado: 'carlos' },
  { nombre: 'Valeria', apellido: 'Castillo', perfil: 'impecable', cat: 'A1', tier: 'platino', arq: 'diligente', asesor: 3 },
  { nombre: 'Andrés', apellido: 'Molina', perfil: 'impecable', cat: 'A1', tier: 'clasica', arq: 'diligente', asesor: 4 },
  { nombre: 'Paola', apellido: 'Rivera', perfil: 'impecable', cat: 'A2', tier: 'oro', arq: 'olvidadizo', asesor: 5 },
  { nombre: 'Diego', apellido: 'Flores', perfil: 'impecable', cat: 'A1', tier: 'black', arq: 'despreocupado', asesor: 6 },
  { nombre: 'Mariana', apellido: 'López', perfil: 'impecable', cat: 'A2', tier: 'clasica', arq: 'olvidadizo', asesor: 7 },
  { nombre: 'Fernando', apellido: 'Guzmán', perfil: 'impecable', cat: 'A1', tier: 'platino', arq: 'diligente', asesor: 0 },
  { nombre: 'Isabel', apellido: 'Navarro', perfil: 'impecable', cat: 'A1', tier: 'oro', arq: 'diligente', asesor: 1 },
  { nombre: 'Ricardo', apellido: 'Pineda', perfil: 'impecable', cat: 'A1', tier: 'clasica', arq: 'despreocupado', asesor: 2 },
  { nombre: 'Camila', apellido: 'Orellana', perfil: 'impecable', cat: 'A1', tier: 'black', arq: 'diligente', asesor: 3 },

  { nombre: 'María', apellido: 'Fernández', perfil: 'mejorable', cat: 'B', tier: 'oro', arq: 'olvidadizo', asesor: 0, legado: 'maria' },
  { nombre: 'Jorge', apellido: 'Herrera', perfil: 'mejorable', cat: 'B', tier: 'clasica', arq: 'despreocupado', asesor: 4 },
  { nombre: 'Daniela', apellido: 'Cruz', perfil: 'mejorable', cat: 'C', tier: 'platino', arq: 'olvidadizo', asesor: 5 },
  { nombre: 'Luis', apellido: 'Mendoza', perfil: 'mejorable', cat: 'B', tier: 'oro', arq: 'diligente', asesor: 6 },
  { nombre: 'Gabriela', apellido: 'Romero', perfil: 'mejorable', cat: 'C', tier: 'clasica', arq: 'resistente', asesor: 7, sinCuenta: true },
  { nombre: 'Óscar', apellido: 'Ventura', perfil: 'mejorable', cat: 'B', tier: 'platino', arq: 'despreocupado', asesor: 0 },
  { nombre: 'Karen', apellido: 'Mejía', perfil: 'mejorable', cat: 'B', tier: 'clasica', arq: 'olvidadizo', asesor: 1 },
  { nombre: 'Kevin', apellido: 'Alas', perfil: 'mejorable', cat: 'C', tier: 'oro', arq: 'resistente', asesor: 2 },
  { nombre: 'Alejandra', apellido: 'Campos', perfil: 'mejorable', cat: 'B', tier: 'black', arq: 'diligente', asesor: 3 },
  { nombre: 'Mauricio', apellido: 'Sosa', perfil: 'mejorable', cat: 'C', tier: 'clasica', arq: 'despreocupado', asesor: 4, sinCuenta: true },
  { nombre: 'Natalia', apellido: 'Díaz', perfil: 'mejorable', cat: 'B', tier: 'oro', arq: 'olvidadizo', asesor: 5 },
  { nombre: 'Héctor', apellido: 'Bonilla', perfil: 'mejorable', cat: 'C', tier: 'platino', arq: 'resistente', asesor: 6 },
  { nombre: 'Tatiana', apellido: 'Serrano', perfil: 'mejorable', cat: 'B', tier: 'clasica', arq: 'diligente', asesor: 7 },
  { nombre: 'Rodrigo', apellido: 'Pacheco', perfil: 'mejorable', cat: 'C', tier: 'oro', arq: 'despreocupado', asesor: 0 },
  { nombre: 'Silvia', apellido: 'Escobar', perfil: 'mejorable', cat: 'B', tier: 'clasica', arq: 'olvidadizo', asesor: 1 },

  { nombre: 'Ernesto', apellido: 'Lemus', perfil: 'fatal', cat: 'D', tier: 'clasica', arq: 'resistente', asesor: 2 },
  { nombre: 'Rocío', apellido: 'Marroquín', perfil: 'fatal', cat: 'E', tier: 'oro', arq: 'despreocupado', asesor: 3 },
  { nombre: 'Julio', apellido: 'Cáceres', perfil: 'fatal', cat: 'D', tier: 'platino', arq: 'olvidadizo', asesor: 4 },
  { nombre: 'Fátima', apellido: 'Argueta', perfil: 'fatal', cat: 'E', tier: 'clasica', arq: 'resistente', asesor: 5, sinCuenta: true },
  { nombre: 'Walter', apellido: 'Peña', perfil: 'fatal', cat: 'D', tier: 'oro', arq: 'despreocupado', asesor: 6 },
  { nombre: 'Yesenia', apellido: 'Ayala', perfil: 'fatal', cat: 'E', tier: 'clasica', arq: 'olvidadizo', asesor: 7 },
  { nombre: 'Nelson', apellido: 'Batres', perfil: 'fatal', cat: 'D', tier: 'black', arq: 'resistente', asesor: 0 },
  { nombre: 'Carolina', apellido: 'Durán', perfil: 'fatal', cat: 'E', tier: 'oro', arq: 'despreocupado', asesor: 1 },
  { nombre: 'Marvin', apellido: 'Henríquez', perfil: 'fatal', cat: 'D', tier: 'clasica', arq: 'olvidadizo', asesor: 2 },
  { nombre: 'Brenda', apellido: 'Linares', perfil: 'fatal', cat: 'E', tier: 'platino', arq: 'resistente', asesor: 3 },
  { nombre: 'Samuel', apellido: 'Quijada', perfil: 'fatal', cat: 'D', tier: 'oro', arq: 'despreocupado', asesor: 4, sinCuenta: true },
  { nombre: 'Patricia', apellido: 'Villalta', perfil: 'fatal', cat: 'E', tier: 'clasica', arq: 'olvidadizo', asesor: 5 },
  { nombre: 'Wilber', apellido: 'Guardado', perfil: 'fatal', cat: 'D', tier: 'platino', arq: 'resistente', asesor: 6 },
  { nombre: 'Sonia', apellido: 'Recinos', perfil: 'fatal', cat: 'E', tier: 'oro', arq: 'despreocupado', asesor: 7 },
  { nombre: 'Rafael', apellido: 'Zelaya', perfil: 'fatal', cat: 'D', tier: 'clasica', arq: 'resistente', asesor: 0 },
];

// El push se decide por ciclo y saldo, nunca por perfil: hay candidatos de los tres.
const CANDIDATOS_PUSH = new Set(['edgar.gomez', 'valeria.castillo', 'andres.molina', 'isabel.navarro', 'jorge.herrera', 'daniela.cruz', 'oscar.ventura', 'natalia.diaz', 'ernesto.lemus', 'julio.caceres', 'nelson.batres', 'brenda.linares']);
const SIN_DISPOSITIVO = new Set(['andres.molina', 'mariana.lopez', 'kevin.alas', 'mauricio.sosa', 'nelson.batres', 'fatima.argueta', 'samuel.quijada']);
const COLORES = ['#7E4FBC', '#00714E', '#1F5FA8', '#B35C00', '#A3316F', '#007A87', '#5A6B7B', '#8A3FFC', '#3E7D1F', '#C2410C'];
const PRODUCTOS_CUENTA = ['Max Electrónico', 'Cuenta Digital', 'Ahorro Programado'];
// Dónde viven (ilustrativo). Nadie trae teléfono: el de la demo se pone con POST /admin/demo/telefono.
const UBICACIONES = [
  ['San Salvador', 'San Salvador'], ['San Salvador', 'San Salvador'], ['Mejicanos', 'San Salvador'], ['Soyapango', 'San Salvador'],
  ['Santa Tecla', 'La Libertad'], ['Antiguo Cuscatlán', 'La Libertad'], ['San Miguel', 'San Miguel'], ['Santa Ana', 'Santa Ana'],
];

// Agencias verificadas en bancoagricola.com/centros-atencion-preferencial (13 sep 2026), con el municipio que
// da esa página.
// El listado completo lo sirve el mapa del banco con JavaScript: cargarlo desde ahí antes de producción.
const HORARIO_830 = 'de lunes a viernes de 8 y media de la mañana a 4 y media de la tarde, y los sábados de 8 y media a 12 del mediodía';
[
  ['suc-merliot', 'Merliot', 'bulevar Merliot y calle L-4, Jardines de la Hacienda, Ciudad Merliot', 'Ciudad Merliot', 'La Libertad', HORARIO_830],
  ['suc-la-mascota', 'La Mascota', 'calle La Mascota, final pasaje A y pasaje número 3', 'San Salvador', 'San Salvador', HORARIO_830],
  ['suc-santa-elena', 'Santa Elena', 'urbanización Santa Elena, final bulevar Santa Elena y bulevar Orden de Malta', 'San Salvador', 'San Salvador', HORARIO_830],
  ['suc-masferrer', 'Masferrer', 'final Paseo General Escalón número 5148', 'San Salvador', 'San Salvador', 'de lunes a viernes de 9 de la mañana a 5 de la tarde, y los sábados de 8 y media a 12 del mediodía'],
  ['suc-clinicas-medicas', 'Clínicas Médicas', '25 avenida norte y 21 calle poniente, frente a la fuente luminosa', 'San Salvador', 'San Salvador', HORARIO_830],
  ['suc-millennium-plaza', 'Millennium Plaza', 'Millennium Plaza, nivel 1, Paseo General Escalón', 'San Salvador', 'San Salvador', 'de lunes a viernes de 9 de la mañana a 5 de la tarde, y los sábados de 9 a 12 del mediodía'],
].forEach(([id, nombre, direccion, municipio, departamento, horario]) =>
  agregar('SUCURSAL', { id, nombre, direccion, municipio, departamento, horario, activo: true }));

const sufijosUsados = new Set(['0110', '4821', '0452', '1001', '1002', '2001', '3001', '3002']);
const nuevoSufijo = () => { let s; do { s = String(entre(1000, 9999)); } while (sufijosUsados.has(s)); sufijosUsados.add(s); return s; };
const numerosUsados = new Set(['3007040110']);
const nuevoNumeroCuenta = (sufijo) => { let n; do { n = `3007${entre(10, 99)}${sufijo}`; } while (numerosUsados.has(n)); numerosUsados.add(n); return n; };
const DIAS_CORTE_FIJOS = [3, 7, 10, 15, 18, 21, 25];

const clientes = CLIENTES.map((c, i) => {
  const username = `${slug(c.nombre)}.${slug(c.apellido)}`;
  const cliente = {
    ...c,
    username,
    id: c.id ?? `cust-${slug(c.nombre)}-${slug(c.apellido)}`,
    asesorDatos: ASESORES[c.asesor],
    cuentas: [],
    creditos: [],
    candidato: CANDIDATOS_PUSH.has(username),
    conDispositivo: !SIN_DISPOSITIVO.has(username),
  };
  const antiguedad = c.perfil === 'impecable' ? entre(30, 80) : c.perfil === 'mejorable' ? entre(14, 40) : entre(4, 20);
  agregar('CLIENTE', {
    id: cliente.id,
    username,
    password_hash: sha256(`ruta:${username}:${CONTRASENA_DEMO}`),
    first_name: c.nombre,
    display_name: `${c.nombre} ${c.apellido}`,
    initials: `${c.nombre[0]}${c.apellido[0]}`.toUpperCase(),
    avatar_color: c.color ?? COLORES[i % COLORES.length],
    voice: 'tu',
    card_tier: c.tier,
    archetype: c.arq,
    first_time_at_risk: c.perfil === 'impecable',
    perfil_crediticio: c.perfil,
    categoria: c.cat,
    asesor_id: cliente.asesorDatos.id,
    // Edgar y un tercio de los demás aún no han respondido «¿Qué día te pagan?».
    frecuencia_pago: c.edgar || i % 3 === 2 ? null : FRECUENCIA_POR_ARQUETIPO[c.arq],
    telefono: null,
    municipio: c.edgar ? 'San Salvador' : null,
    departamento: c.edgar ? 'San Salvador' : null,
    fecha_alta: sumarMeses(HOY, -antiguedad),
    activo: true,
  });
  if (!c.edgar) {
    const [municipio, departamento] = elegirVoz(UBICACIONES);
    Object.assign(filas.get('CLIENTE').at(-1), { municipio, departamento });
  }
  return cliente;
});

// ---------------------------------------------------------------------------
// Cuentas y créditos
// ---------------------------------------------------------------------------
function crearCuenta(cliente, datos) {
  const cuenta = { ...datos, cliente_id: cliente.id };
  agregar('CUENTA', {
    id: cuenta.id, cliente_id: cliente.id, tipo: cuenta.tipo, product_name: cuenta.product_name,
    number_masked: `····${cuenta.sufijo}`, number_full: cuenta.number_full, balance_available: cuenta.balance,
    balance_apartado: 0, currency: 'USD', is_primary_source: cuenta.primaria, created_at: momentoRelativo(-entre(200, 900)),
  });
  cliente.cuentas.push(cuenta);
  return cuenta;
}

function crearCredito(cliente, datos) {
  const credito = { ...datos, cliente };
  const candidato = cliente.candidato && datos.principal;
  const fila = {
    id: datos.id, cliente_id: cliente.id, kind: datos.kind, name: datos.name,
    number_masked: `····${datos.sufijo}`, currency: 'USD', apartable: datos.apartable,
    credit_limit: null, available: null, used_pct: null, pay_contado: null,
    installment_amount: null, current_due_day: null, operation_number: null, saldo_capital: null, tasa_anual: null,
    dia_corte: candidato ? DIA_CORTE_CANDIDATO : datos.dia_corte,
    fecha_apertura: datos.fecha_apertura,
    estado_pago: datos.estado_pago ?? 'al_dia',
  };
  if (datos.kind === 'card') {
    Object.assign(fila, { credit_limit: datos.limite, available: datos.disponible, used_pct: datos.uso, pay_contado: datos.contado });
    credito.base = datos.contado;
  } else {
    Object.assign(fila, { installment_amount: datos.cuota, current_due_day: datos.dia_pago, operation_number: datos.operacion });
    // Saldo y tasa ilustrativos hasta confirmarlos con la cartera real: base del interés al cambiar la fecha.
    const tasas = { personal: [14, 22], hipotecario: [8, 10], bancario: [11, 16] }[datos.kind];
    Object.assign(fila, { saldo_capital: dinero(datos.cuota * entreVoz(12, 48)), tasa_anual: entreVoz(tasas[0], tasas[1]) / 100 });
    credito.base = datos.cuota;
  }
  credito.candidato = candidato;
  agregar('CREDITO', fila);
  cliente.creditos.push(credito);
  return credito;
}

const aperturaPorPerfil = (perfil) =>
  perfil === 'impecable' ? sumarMeses(HOY, -entre(20, 70)) : perfil === 'mejorable' ? sumarMeses(HOY, -entre(10, 30)) : sumarMeses(HOY, -entre(3, 16));

function tarjeta(cliente, principal, opciones = {}) {
  const sufijo = opciones.sufijo ?? nuevoSufijo();
  const limite = opciones.limite ?? elegir(cliente.perfil === 'impecable' ? [1500, 2000, 3000, 5000] : cliente.perfil === 'mejorable' ? [800, 1000, 1500, 2000] : [500, 800, 1000]);
  const uso = opciones.uso ?? (cliente.perfil === 'impecable' ? entre(10, 35) : cliente.perfil === 'mejorable' ? entre(45, 75) : entre(80, 97));
  const contado = dinero(limite * uso / 100);
  return crearCredito(cliente, {
    id: opciones.id ?? `cred-card-${sufijo}`, kind: 'card', name: opciones.name ?? 'Tarjeta de crédito', sufijo,
    apartable: opciones.apartable ?? true, limite, uso, contado, disponible: dinero(limite - contado),
    dia_corte: opciones.dia_corte ?? elegir(DIAS_CORTE_FIJOS), fecha_apertura: opciones.apertura ?? aperturaPorPerfil(cliente.perfil),
    estado_pago: opciones.estado_pago, principal,
  });
}

const NOMBRE_CREDITO = { personal: 'Crédito personal', hipotecario: 'Crédito hipotecario', bancario: 'Crédito bancario' };

function personal(cliente, principal, opciones = {}) {
  const kind = opciones.kind ?? 'personal';
  const sufijo = opciones.sufijo ?? nuevoSufijo();
  const rango = kind === 'hipotecario' ? [38000, 95000] : kind === 'bancario' ? [12000, 40000]
    : cliente.perfil === 'impecable' ? [18000, 45000] : cliente.perfil === 'mejorable' ? [9000, 35000] : [8500, 30000];
  const cuota = opciones.cuota ?? dinero(entre(rango[0], rango[1]) / 100);
  const diaPago = opciones.dia_pago ?? elegir([5, 10, 15, 20, 25, 28]);
  return crearCredito(cliente, {
    id: opciones.id ?? `cred-${kind}-${sufijo}`, kind, name: opciones.name ?? NOMBRE_CREDITO[kind], sufijo,
    apartable: true, cuota, dia_pago: diaPago, operacion: opciones.operacion ?? String(entre(3000000, 3999999)),
    dia_corte: opciones.dia_corte ?? Math.min(diaPago, 28), fecha_apertura: opciones.apertura ?? aperturaPorPerfil(cliente.perfil),
    estado_pago: opciones.estado_pago, principal,
  });
}

for (const cliente of clientes) {
  const pendiente = cliente.perfil === 'fatal' || cliente.perfil === 'mejorable' ? 'parte_pendiente' : 'al_dia';

  if (cliente.edgar) {
    crearCuenta(cliente, { id: 'acc-0110', tipo: 'savings', product_name: 'Max Electrónico', sufijo: '0110', number_full: '3007040110', balance: 1482.14, primaria: true });
    tarjeta(cliente, false, { id: 'cred-card-4821', sufijo: '4821', limite: 1000, uso: 34, dia_corte: 7, apertura: new Date(2022, 2, 14) });
    personal(cliente, true, { id: 'cred-personal-0452', sufijo: '0452', cuota: 248.5, dia_pago: 28, operacion: '3242785', apertura: new Date(2024, 3, 28) });
    continue;
  }

  if (!cliente.sinCuenta) {
    const sufijo = nuevoSufijo();
    const balance = cliente.perfil === 'impecable' ? entre(90000, 650000) / 100 : cliente.perfil === 'mejorable' ? entre(15000, 90000) / 100 : entre(0, 12000) / 100;
    crearCuenta(cliente, {
      id: `acc-${sufijo}`, tipo: azar() < 0.7 ? 'savings' : 'debit', product_name: elegir(PRODUCTOS_CUENTA),
      sufijo, number_full: nuevoNumeroCuenta(sufijo), balance: dinero(balance), primaria: true,
    });
    if (cliente.perfil === 'impecable' && azar() < 0.5) {
      const s2 = nuevoSufijo();
      crearCuenta(cliente, { id: `acc-${s2}`, tipo: 'savings', product_name: 'Ahorro Programado', sufijo: s2, number_full: nuevoNumeroCuenta(s2), balance: dinero(entre(20000, 300000) / 100), primaria: false });
    }
  }

  if (cliente.legado === 'lucia') {
    tarjeta(cliente, false, { id: 'cred-card-3001', sufijo: '3001', name: 'Línea de crédito', limite: 5000, uso: 16, dia_corte: 7, apertura: new Date(2025, 2, 1) });
    personal(cliente, true, { id: 'cred-personal-3002', sufijo: '3002', name: 'Préstamo comercial', cuota: 1000, dia_pago: 7, operacion: '30003002', apertura: new Date(2023, 4, 5) });
  } else if (cliente.legado === 'carlos') {
    personal(cliente, true, { id: 'cred-personal-2001', sufijo: '2001', cuota: 500, dia_pago: 21, operacion: '10002001', apertura: new Date(2024, 0, 10) });
  } else if (cliente.legado === 'maria') {
    tarjeta(cliente, true, { id: 'cred-card-1001', sufijo: '1001', limite: 3000, uso: 58, dia_corte: 7, apertura: new Date(2025, 5, 15), estado_pago: pendiente });
    tarjeta(cliente, false, { id: 'cred-card-1002', sufijo: '1002', name: 'Línea de crédito', limite: 2500, uso: 44, dia_corte: 21, apertura: new Date(2025, 10, 20) });
  } else {
    const soloTarjeta = azar() < 0.18;
    if (soloTarjeta) {
      tarjeta(cliente, true, { estado_pago: pendiente });
    } else {
      personal(cliente, true, { estado_pago: pendiente });
      if (azar() < (cliente.perfil === 'fatal' ? 0.45 : 0.6)) tarjeta(cliente, false, { apartable: azar() < 0.7 });
    }
    // Productos bancarios adicionales: hipotecarios en impecables, bancarios en mejorables y fatales.
    if (cliente.perfil === 'impecable' && azar() < 0.45) personal(cliente, false, { kind: 'hipotecario', dia_pago: elegir([5, 10, 20]) });
    else if (cliente.perfil !== 'impecable' && azar() < 0.3) personal(cliente, false, { kind: 'bancario', dia_pago: elegir([10, 15, 25]) });
  }
}

// Productos disponibles ya activados (el depósito a plazo deja de ofrecerse).
for (const cliente of clientes) {
  if (cliente.edgar || cliente.perfil !== 'impecable' || azar() > 0.5) continue;
  agregar('PRODUCTO_ACTIVADO', {
    id: `prod-${slug(cliente.nombre)}-${slug(cliente.apellido)}-deposito`, cliente_id: cliente.id, oferta_id: 'offer-term-deposit',
    detalle: 'Depósito a plazo digital a 12 meses', created_at: momentoRelativo(-entre(30, 300)),
  });
}

const principal = (cliente) => cliente.creditos.find((c) => c.principal) ?? cliente.creditos[0];
const cuentaPrimaria = (cliente) => cliente.cuentas.find((c) => c.primaria);

// ---------------------------------------------------------------------------
// Ledger (TRANSACCION)
// ---------------------------------------------------------------------------
for (const cliente of clientes) {
  for (const credito of cliente.creditos) {
    let n = 0;
    const tx = (fila) => agregar('TRANSACCION', { id: `tx-${credito.sufijo}-${++n}`, credito_id: credito.id, numero_producto: `····${credito.sufijo}`, frontera_contada: null, ...fila });
    const incidencias = !credito.principal ? 0 : cliente.perfil === 'mejorable' ? entre(1, 2) : cliente.perfil === 'fatal' ? entre(3, 4) : 0;
    const fronteras = cliente.perfil === 'mejorable' ? ['A_B', 'B_C'] : ['B_C', 'C_D', 'D_E', 'C_D'];
    const conceptoCargo = credito.kind === 'personal' ? 'Cuota mensual' : 'Consumos del ciclo';
    // Historial fuera del ciclo vigente (siempre antes de 32 días).
    [-125, -95, -65, -35].forEach((dia, k) => {
      const cargo = credito.kind === 'personal' ? credito.base : dinero(credito.base * (0.55 + azar() * 0.45));
      tx({ fecha: fechaRelativa(dia), tipo: 'D', monto: cargo, descripcion: conceptoCargo });
      const conIncidencia = k < incidencias;
      const abono = cliente.perfil === 'fatal' && conIncidencia && k % 2 === 1 ? dinero(cargo * 0.5) : cargo;
      tx({
        fecha: fechaRelativa(dia + 3), tipo: 'H', monto: abono,
        descripcion: abono < cargo ? 'Abono parcial' : credito.kind === 'personal' ? 'Pago de cuota' : 'Pago de tarjeta',
        frontera_contada: conIncidencia ? fronteras[k % fronteras.length] : null,
      });
    });
    // Ciclo vigente: los candidatos quedan con saldo; el resto, saldado.
    tx({ fecha: fechaRelativa(-2), tipo: 'D', monto: credito.base, descripcion: conceptoCargo });
    if (!credito.candidato) {
      tx({ fecha: fechaRelativa(-2), tipo: 'H', monto: credito.base, descripcion: credito.kind === 'personal' ? 'Pago de cuota' : 'Pago de tarjeta' });
    }
  }
}

// ---------------------------------------------------------------------------
// Ruta: fecha de cobro, apartado y autopago
// ---------------------------------------------------------------------------
const PLAN_POR_ARQUETIPO = {
  diligente: [['freq-quincena-finmes', 'date-18', 18], ['freq-quincena-finmes', 'date-19', 19]],
  olvidadizo: [['freq-solo-finmes', 'date-3', 3], ['freq-solo-finmes', 'date-4', 4]],
  despreocupado: [['freq-variable', 'date-18', 18]],
  resistente: [['freq-semanal', 'date-lun', 0], ['freq-semanal', 'date-vie', 0]],
};
const planes = new Map();
const otros = clientes.filter((c) => !c.edgar);
const cuposPlan = { impecable: 8, mejorable: 12, fatal: 10 };
for (const cliente of otros) {
  if (cuposPlan[cliente.perfil] <= 0) continue;
  const credito = principal(cliente);
  const [frecuencia_id, opcion_id, new_day] = elegir(PLAN_POR_ARQUETIPO[cliente.arq]);
  const desde = new_day === 0 ? sumarDias(HOY, 7 - HOY.getDay()) : proximoDia(sumarDias(HOY, 10), new_day);
  const effective = new_day === 0
    ? COPY['fecha.semanal_desde']
    : render(COPY['fecha.desde'], { fecha: etiquetaLarga(desde) });
  const hace = entre(20, 120);
  const meses = Number(COPY['fecha.meses_bloqueo']);
  agregar('PLAN_FECHA_COBRO', {
    credito_id: credito.id, new_day, effective_from_label: effective, effective_from: desde, amount_unchanged: true, term_unchanged: true,
    frecuencia_id, opcion_id, dias_extra: 0, interes_extra: 0, acepto_interes: false, canal: 'app',
    bloqueado_hasta: expr(`ADD_MONTHS(TRUNC(SYSDATE) - ${hace}, ${meses})`, `DATEADD(MONTH, ${meses}, DATEADD(DAY, -${hace}, CURRENT_DATE))`),
    created_at: momentoRelativo(-hace),
  });
  planes.set(cliente.id, { frecuencia_id, new_day });
  cuposPlan[cliente.perfil]--;
}

const apartadosActivos = new Map();
const autopagosActivos = new Set();
function crearAutopago(credito, cuenta, activo, dias) {
  if (activo && autopagosActivos.has(credito.id)) return;
  if (activo) autopagosActivos.add(credito.id);
  agregar('AUTOPAGO', { id: `autopay-${credito.sufijo}-${activo ? 'a' : 'r'}${dias}`, credito_id: credito.id, account_id: cuenta.id, active: activo, created_at: momentoRelativo(-dias) });
}

const DESPLAZAMIENTOS = {
  activo: [2, 17, 32, 47],
  cumplido: [-80, -65, -50, -35],
  cancelado: [-40, -25, -10, 5],
};
let apartadoN = 0;
for (const cliente of otros) {
  const cuenta = cuentaPrimaria(cliente);
  if (!cuenta) continue;
  const estado = cliente.perfil === 'mejorable' ? 'activo' : cliente.perfil === 'impecable' ? 'cumplido' : 'cancelado';
  const credito = principal(cliente);
  const partes = [2, 3, 4][apartadoN++ % 3];
  const cada = dinero(credito.base / partes);
  const offsets = DESPLAZAMIENTOS[estado].slice(0, partes);
  const pagoDia = offsets[offsets.length - 1] + 3;
  const automatico = cliente.arq === 'olvidadizo' || (estado === 'activo' && azar() < 0.4);
  const id = `apartado-${credito.sufijo}`;
  agregar('APARTADO', {
    id, credito_id: credito.id, parts: partes, source_account_id: cuenta.id,
    pays_on_label: etiquetaRelativa(pagoDia, 'se paga completo el '), automatic: automatico,
    first_full_installment_label: etiquetaRelativa(pagoDia, 'primera cuota completa el '),
    monto_total: credito.base, fecha_pago: fechaRelativa(pagoDia), estado, created_at: momentoRelativo(offsets[0] - 5),
  });
  offsets.forEach((dia, i) => agregar('APARTADO_CUOTA', {
    apartado_id: id, idx: i + 1, label: etiquetaRelativa(dia), fecha: fechaRelativa(dia),
    amount: i === partes - 1 ? dinero(credito.base - cada * (partes - 1)) : cada,
    estado: estado === 'cumplido' ? 'apartada' : 'pendiente',
  }));
  if (estado === 'activo') apartadosActivos.set(cliente.id, { partes, credito });
  if (automatico) crearAutopago(credito, cuenta, estado !== 'cancelado', entre(5, 60));
}

for (const cliente of otros) {
  const cuenta = cuentaPrimaria(cliente);
  if (!cuenta) continue;
  for (const credito of cliente.creditos) {
    if (filas.get('AUTOPAGO').length >= 34) break;
    if (autopagosActivos.has(credito.id)) continue;
    if (cliente.perfil === 'fatal') crearAutopago(credito, cuenta, false, entre(15, 90));
    else if (azar() < 0.55) crearAutopago(credito, cuenta, true, entre(10, 150));
  }
}

// ---------------------------------------------------------------------------
// Contexto de choque
// ---------------------------------------------------------------------------
const choques = new Map();
for (const cliente of clientes) {
  if (cliente.edgar) {
    const c = { event_label: 'La parte del 15 de noviembre no alcanzó', reassurance: 'Le pasa a cualquiera, y tiene arreglo.', amount: 124.25, next_date_label: '18 de diciembre', credito: principal(cliente) };
    choques.set(cliente.id, c);
    continue;
  }
  if (cliente.perfil === 'impecable') continue;
  const credito = principal(cliente);
  const mes = MESES[HOY.getMonth()];
  const c = cliente.perfil === 'mejorable'
    ? { event_label: `La parte del ${elegir([15, 30])} de ${mes} quedó corta`, reassurance: 'Le pasa a cualquiera, y tiene arreglo.', amount: dinero(credito.base * elegir([0.3, 0.4, 0.5])) }
    : { event_label: `Las partes de ${mes} no alcanzaron para tu cuota`, reassurance: 'Hay salidas, y las vemos contigo.', amount: credito.base };
  c.next_date_label = etiquetaLarga(proximoDia(sumarDias(HOY, 7), elegir([18, 19, 3, 4])));
  c.credito = credito;
  choques.set(cliente.id, c);
}
for (const [cliente_id, c] of choques) {
  agregar('SHOCK_CONTEXT', {
    cliente_id, credito_id: c.credito.id, event_label: c.event_label, reassurance: c.reassurance,
    amount: c.amount, currency: 'USD', next_date_label: c.next_date_label, created_at: momentoRelativo(0, -300),
  });
}

// ---------------------------------------------------------------------------
// Citas
// ---------------------------------------------------------------------------
const TEMAS = { personal: ['topic-personal', 'crédito personal'], card: ['topic-card', 'tarjeta de crédito'] };
const cuposCita = { impecable: 7, mejorable: 10, fatal: 15 };
const citas = new Map();
let citaN = 0;
for (const cliente of otros) {
  if (cuposCita[cliente.perfil] <= 0) continue;
  cuposCita[cliente.perfil]--;
  const credito = principal(cliente);
  const [tema_id, tema] = TEMAS[credito.kind];
  const [dia_id, , whenLabel] = DIAS_ASESORIA[citaN % DIAS_ASESORIA.length];
  const [hora_id, horaLabel] = HORAS_ASESORIA[(citaN * 3) % HORAS_ASESORIA.length];
  const asesor = cliente.asesorDatos;
  const cancelada = citaN % 4 === 3;
  const cita = {
    id: `appt-${slug(cliente.nombre)}-${slug(cliente.apellido)}`, cliente_id: cliente.id, asesor_id: asesor.id, tema_id, producto_id: credito.id,
    dia_id, hora_id, with_label: asesor.name, when_label: render(COPY['cita.cuando'], { dia: whenLabel, hora: horaLabel }),
    where_label: render(COPY['cita.donde'], { agencia: asesor.agency }), about_label: render(COPY['cita.sobre'], { tema }),
    confirmation_note: render(COPY['cita.nota'], { asesora: asesor.name.split(' ')[0], tema }),
    status: cancelada ? 'cancelled' : 'scheduled', source: choques.has(cliente.id) && citaN % 3 !== 0 ? 'shock' : 'chat',
    created_at: momentoRelativo(-entre(1, 40)),
  };
  agregar('CITA', cita);
  citas.set(cliente.id, cita);
  citaN++;
}

// ---------------------------------------------------------------------------
// Avisos (confirman lo ocurrido, nunca recuerdan pagar)
// ---------------------------------------------------------------------------
function aviso(cliente, n, datos) {
  agregar('AVISO', {
    id: datos.id ?? `aviso-${slug(cliente.nombre)}-${slug(cliente.apellido)}-${n}`, cliente_id: cliente.id,
    kind: datos.kind, title: datos.title, body: datos.body ?? null, time_label: datos.time_label ?? null,
    date_label: datos.date_label, read_flag: datos.read ?? true, actionable: !!datos.target, target: datos.target ?? null,
    orden: n, created_at: datos.created_at ?? momentoRelativo(-n * 3),
  });
}
for (const cliente of clientes) {
  if (cliente.edgar) {
    // Edgar: los cuatro avisos de la vista 11, con cuerpo y con fecha real (hoy 8:05 y semanas atrás).
    aviso(cliente, 1, { id: 'notice-shock', kind: 'shock', title: 'Este mes vino distinto, y está bien', body: 'No alcanzó para apartar los $124.25 de tu quincena. Toca aquí y tu asesor te ayuda a ajustarlo. Sin costo.', time_label: '8:05', date_label: 'HOY', read: false, target: 'advisory-shock', created_at: momentoRelativo(0, -55) });
    aviso(cliente, 2, { id: 'notice-mitad', kind: 'progress', title: 'Ya va la mitad', body: 'Apartamos $124.25 de tu pago. Tu cuota del 18 va a la mitad.', date_label: '30 oct', created_at: momentoRelativo(-17, 30) });
    aviso(cliente, 3, { id: 'notice-pagado', kind: 'paid', title: 'Pagado, y a tiempo', body: 'Tu cuota de $248.50 se pagó sola. Ya son 19 meses seguidos: mira tu récord.', date_label: '18 oct', target: 'record', created_at: momentoRelativo(-29, 5) });
    aviso(cliente, 4, { id: 'notice-completa', kind: 'complete', title: 'Tu cuota ya está completa', body: 'Apartamos los últimos $124.25. El 18 se paga sola. Tú no tienes que hacer nada.', date_label: '15 oct', created_at: momentoRelativo(-32, 30) });
    continue;
  }
  let n = 0;
  const choque = choques.get(cliente.id);
  if (choque) {
    aviso(cliente, ++n, { kind: 'shock', title: 'Este mes vino distinto, y está bien', body: `${choque.event_label}. Podemos verlo juntos.`, time_label: `${entre(7, 9)}:${String(entre(0, 59)).padStart(2, '0')}`, date_label: 'HOY', read: false, target: 'advisory-shock' });
  }
  const plan = planes.get(cliente.id);
  if (plan) {
    aviso(cliente, ++n, { kind: 'confirm', title: plan.new_day === 0 ? 'Tu cobro quedó cada semana' : `Tu fecha de cobro quedó en el día ${plan.new_day}`, date_label: etiquetaCorta(sumarDias(HOY, -entre(8, 20))) });
  }
  const activo = apartadosActivos.get(cliente.id);
  if (activo) {
    aviso(cliente, ++n, { kind: 'progress', title: `Llevas 1 de ${activo.partes} partes apartadas`, date_label: etiquetaCorta(sumarDias(HOY, -entre(2, 6))) });
  }
  if (citas.get(cliente.id)?.status === 'scheduled') {
    aviso(cliente, ++n, { kind: 'confirm', title: 'Tu asistencia quedó agendada', body: citas.get(cliente.id).when_label, date_label: etiquetaCorta(sumarDias(HOY, -entre(1, 5))) });
  }
  if (cliente.perfil === 'impecable') {
    aviso(cliente, ++n, { kind: 'paid', title: 'Pagado, y a tiempo', date_label: etiquetaCorta(sumarDias(HOY, -entre(10, 25))), target: 'record' });
    aviso(cliente, ++n, { kind: 'complete', title: 'Tu cuota ya está completa', date_label: etiquetaCorta(sumarDias(HOY, -entre(26, 40))) });
    aviso(cliente, ++n, { kind: 'progress', title: 'Ya va la mitad', date_label: etiquetaCorta(sumarDias(HOY, -entre(41, 55))) });
  } else if (cliente.perfil === 'mejorable') {
    aviso(cliente, ++n, { kind: 'paid', title: 'Pagado, y a tiempo', date_label: etiquetaCorta(sumarDias(HOY, -entre(30, 45))), target: 'record' });
    aviso(cliente, ++n, { kind: 'complete', title: 'Tu cuota ya está completa', date_label: etiquetaCorta(sumarDias(HOY, -entre(46, 60))) });
  } else {
    aviso(cliente, ++n, { kind: 'confirm', title: 'Recibimos tu abono', date_label: etiquetaCorta(sumarDias(HOY, -entre(20, 35))) });
    aviso(cliente, ++n, { kind: 'confirm', title: 'Guardamos tu conversación con la asesoría', date_label: etiquetaCorta(sumarDias(HOY, -entre(36, 50))) });
  }
}

// ---------------------------------------------------------------------------
// Récord (solo suma; los atrasos viejos se muestran como fecha de salida)
// ---------------------------------------------------------------------------
const NOTA_CONSULTAS = 'Consulta tu récord sin costo y sin límite.';
for (const cliente of clientes) {
  const hitos = [];
  const sumandos = [];
  let racha;
  let progreso;
  if (cliente.edgar) {
    agregar('RECORD_PAGO', { cliente_id: cliente.id, streak_months: 19, next_plus_one_label: 'Tu próximo +1: 18 de noviembre', progress_current: 22, progress_total: 24, consults_note: NOTA_CONSULTAS });
    hitos.push(['23 de 24', '23 feb 2027'], ['24 de 24', '24 mar 2027']);
    sumandos.push(['sum-1', 'Pagaste octubre a tiempo'], ['sum-2', 'Cambiaste tu fecha de cobro'], ['sum-3', 'Apartas tu cuota en partes']);
  } else {
    const mesPasado = MESES[(HOY.getMonth() + 11) % 12];
    if (cliente.perfil === 'impecable') {
      racha = entre(18, 36);
      progreso = entre(18, 22);
      hitos.push([`${progreso + 1} de 24`, etiquetaAnio(sumarMeses(HOY, 1))], [`${progreso + 2} de 24`, etiquetaAnio(sumarMeses(HOY, 2))]);
      sumandos.push([null, `Pagaste ${mesPasado} a tiempo`], [null, `Llevas ${racha} meses seguidos`]);
      if (autopagosActivos.has(principal(cliente).id)) sumandos.push([null, 'Tienes tu cuota en automático']);
    } else if (cliente.perfil === 'mejorable') {
      racha = entre(3, 12);
      progreso = entre(8, 16);
      hitos.push(['Sale de tu historial', etiquetaAnio(sumarMeses(HOY, entre(4, 8)))], [`${progreso + 1} de 24`, etiquetaAnio(sumarMeses(HOY, 1))]);
      sumandos.push([null, `Resolviste la parte de ${mesPasado}`], [null, 'Consultas tu récord seguido']);
      if (apartadosActivos.has(cliente.id)) sumandos.push([null, 'Apartas tu cuota en partes']);
    } else {
      racha = entre(0, 2);
      progreso = entre(0, 5);
      hitos.push([progreso === 0 ? 'Tu primer +1' : `${progreso + 1} de 24`, etiquetaAnio(sumarMeses(HOY, 1))], ['Sale de tu historial', etiquetaAnio(sumarMeses(HOY, entre(10, 18)))]);
      sumandos.push([null, 'Abriste tu chat con la asesoría'], [null, `Recibimos tu abono de ${mesPasado}`]);
      if (citas.get(cliente.id)?.status === 'scheduled') sumandos.push([null, 'Agendaste tu asistencia']);
    }
    agregar('RECORD_PAGO', {
      cliente_id: cliente.id, streak_months: racha,
      next_plus_one_label: `Tu próximo +1: ${etiquetaLarga(proximoDia(HOY, elegir([3, 4, 18, 19])))}`,
      progress_current: progreso, progress_total: 24, consults_note: NOTA_CONSULTAS,
    });
  }
  hitos.forEach(([label, date_label], i) => agregar('RECORD_HITO', { cliente_id: cliente.id, idx: i + 1, label, date_label }));
  sumandos.forEach(([id, label], i) => agregar('RECORD_SUMANDO', {
    id: id ?? `sum-${slug(cliente.nombre)}-${slug(cliente.apellido)}-${i + 1}`, cliente_id: cliente.id, label, orden: i + 1,
  }));
}

// ---------------------------------------------------------------------------
// Conversaciones del asesor (transcripción + resultado + fecha acordada)
// ---------------------------------------------------------------------------
const TIERS_ASESORA = new Set(['platino', 'black']);
function ofertasPara(cliente) {
  const orden = COPY[`chat.orden.${cliente.arq}`].split(',');
  return cliente.cuentas.length ? orden : orden.filter((id) => id !== 'qr-automatico');
}

const ESCENARIOS = {
  'acuerdo-apartar': (cliente, v) => ({
    mensajes: [
      ['user', elegir(['Hola, este mes no me alcanzó para la cuota completa', 'Buenas, se me complicó el pago de este mes', 'Hola, la quincena no me alcanzó'])],
      ['assistant', choques.has(cliente.id) ? render(COPY[`chat.apertura.shock.${cliente.arq}`], v) : render(COPY['chat.apertura.general'], v), 'guion', ofertasPara(cliente)],
      ['user', ETIQUETA_OPCION['qr-apartar']],
      ['assistant', render(COPY['chat.acuerdo.apartar'], v), 'guion'],
    ],
    resultado: 'acuerdo', oferta: 'qr-apartar',
  }),
  'acuerdo-fecha': (cliente, v) => ({
    mensajes: [
      ['user', '¿Puedo mover mi cuota para después de mi quincena?'],
      ['assistant', `Claro, ${v.nombre}. La mayoría de clientes como tú mueve su fecha de cobro a un día después de que le pagan, y el monto y el plazo no cambian. ¿Lo vemos ahora?`, 'modelo', ofertasPara(cliente), 'gemini'],
      ['user', ETIQUETA_OPCION['qr-fecha']],
      ['assistant', render(COPY['chat.acuerdo.fecha'], { ...v, costo: COPY['chat.costo.sin_interes'] }), 'guion'],
    ],
    resultado: 'acuerdo', oferta: 'qr-fecha',
  }),
  'acuerdo-automatico': (cliente, v) => ({
    mensajes: [
      ['user', 'Siempre se me pasa la fecha de pago'],
      ['assistant', `Le pasa a muchas personas, ${v.nombre}, y tiene arreglo sencillo. Si lo dejamos en automático desde tu cuenta, de ahí se aparta y de ahí se paga solo. ¿Te sirve?`, 'modelo', ofertasPara(cliente), 'groq'],
      ['user', ETIQUETA_OPCION['qr-automatico']],
      ['assistant', render(COPY['chat.acuerdo.automatico'], v), 'guion'],
    ],
    resultado: 'acuerdo', oferta: 'qr-automatico',
  }),
  'redireccion-acuerdo': (cliente, v) => ({
    mensajes: [
      ['user', '¿Me recomiendas una serie para ver hoy?'],
      ['assistant', render(COPY['chat.redireccion'], v), 'modelo', ofertasPara(cliente), 'gemini'],
      ['user', ETIQUETA_OPCION['qr-apartar']],
      ['assistant', render(COPY['chat.acuerdo.apartar'], v), 'guion'],
    ],
    resultado: 'acuerdo', oferta: 'qr-apartar',
  }),
  'siguiente-paso': (cliente, v) => ({
    mensajes: [
      ['user', 'Quiero ver qué opciones tengo'],
      ['assistant', render(COPY['chat.ofertas'], v), 'guion', ofertasPara(cliente)],
      ['user', ETIQUETA_OPCION['qr-lo-pienso']],
      ['assistant', render(COPY['chat.siguiente_paso'], v), 'guion'],
    ],
    resultado: 'siguiente_paso',
  }),
  escalado: (cliente, v) => {
    const asesora = TIERS_ASESORA.has(cliente.tier);
    return {
      mensajes: [
        ['user', elegir(['No puedo pagar nada este mes, me quedé sin trabajo', 'Necesito hablar con una persona', 'Este mes no tengo cómo cubrir nada'])],
        ['assistant', render(COPY[asesora ? 'chat.escalar.asesora' : 'chat.escalar.centro'], v), 'guion', ['qr-agendar', 'qr-no-gracias']],
        ['user', ETIQUETA_OPCION['qr-agendar']],
        ['assistant', render(COPY[asesora ? 'chat.cita.agendada' : 'chat.centro.confirmado'], v), 'guion'],
      ],
      resultado: 'escalado',
    };
  },
  negativa: (cliente, v) => {
    const asesora = TIERS_ASESORA.has(cliente.tier);
    return {
      mensajes: [
        ['user', 'No me interesa ninguna opción'],
        ['assistant', render(COPY[asesora ? 'chat.escalar.asesora' : 'chat.escalar.centro'], v), 'guion', ['qr-agendar', 'qr-no-gracias']],
        ['user', ETIQUETA_OPCION['qr-no-gracias']],
        ['assistant', render(COPY['chat.negativa'], v), 'guion'],
      ],
      resultado: 'negativa', rechazos: 1,
    };
  },
  'en-curso': (cliente, v) => ({
    mensajes: [
      ['user', 'Hola, tengo una duda con mi cuota'],
      ['assistant', `Hola, ${v.nombre}. Con gusto te ayudo con tu cuota. Cuéntame qué pasó y vemos la opción que mejor te acomode.`, 'modelo', ofertasPara(cliente), 'groq'],
    ],
    resultado: 'en_curso',
  }),
};

const REPARTO_CHATS = {
  impecable: ['acuerdo-fecha', 'acuerdo-automatico', 'acuerdo-fecha', 'siguiente-paso', 'redireccion-acuerdo', 'acuerdo-automatico', 'acuerdo-fecha', 'siguiente-paso', 'en-curso'],
  mejorable: ['acuerdo-apartar', 'acuerdo-automatico', 'escalado', 'siguiente-paso', 'acuerdo-apartar', 'redireccion-acuerdo', 'acuerdo-apartar', 'siguiente-paso', 'escalado', 'acuerdo-automatico', 'acuerdo-apartar', 'siguiente-paso', 'escalado'],
  fatal: ['escalado', 'negativa', 'escalado', 'escalado', 'negativa', 'escalado', 'en-curso', 'escalado', 'negativa', 'escalado', 'escalado', 'negativa', 'en-curso', 'escalado'],
};
const indiceReparto = { impecable: 0, mejorable: 0, fatal: 0 };
for (const cliente of otros) {
  const lista = REPARTO_CHATS[cliente.perfil];
  if (indiceReparto[cliente.perfil] >= lista.length) continue;
  let escenario = lista[indiceReparto[cliente.perfil]++];
  if (escenario === 'acuerdo-automatico' && !cliente.cuentas.length) escenario = 'acuerdo-fecha';
  if (cliente.arq === 'resistente' && escenario.startsWith('acuerdo')) escenario = 'negativa';

  const credito = principal(cliente);
  const choque = choques.get(cliente.id);
  const hace = entre(2, 45);
  const diasAcuerdo = entre(8, 25);
  const cita = citas.get(cliente.id);
  const v = {
    nombre: cliente.nombre,
    asesora: cliente.asesorDatos.name,
    evento: choque ? minuscula(choque.event_label) : '',
    tranquilidad: choque?.reassurance ?? COPY['chat.tranquilidad'],
    monto: monto(credito.base),
    partes: '2 partes',
    dia: 18,
    fecha: etiquetaLarga(sumarDias(HOY, -hace + diasAcuerdo)),
    cuenta: cuentaPrimaria(cliente) ? `····${cuentaPrimaria(cliente).sufijo}` : '',
    cuando: cita?.when_label ?? render(COPY['cita.cuando'], { dia: DIAS_ASESORIA[1][2], hora: HORAS_ASESORIA[1][1] }),
    donde: render(COPY['cita.donde'], { agencia: cliente.asesorDatos.agency }),
    tema: TEMAS[credito.kind][1],
  };
  const guion = ESCENARIOS[escenario](cliente, v);
  const sesionId = `chat-${slug(cliente.nombre)}-${slug(cliente.apellido)}`;
  const turnos = guion.mensajes.filter(([rol]) => rol === 'user').length;
  const minutoFinal = (guion.mensajes.length - 1) * 2;
  const cerrado = guion.resultado !== 'en_curso';
  agregar('CHAT_SESION', {
    id: sesionId, cliente_id: cliente.id, aviso_id: null, resultado: guion.resultado, oferta_aceptada: guion.oferta ?? null,
    fecha_acordada: guion.resultado === 'acuerdo' ? fechaRelativa(-hace + diasAcuerdo) : null,
    turnos, rechazos: guion.rechazos ?? 0, created_at: momentoRelativo(-hace), updated_at: momentoRelativo(-hace, minutoFinal),
    closed_at: cerrado ? momentoRelativo(-hace, minutoFinal) : null,
  });
  guion.mensajes.forEach(([rol, texto, origen, opciones, proveedor], i) => {
    const mensajeId = `msg-${slug(cliente.nombre)}-${slug(cliente.apellido)}-${i + 1}`;
    agregar('CHAT_MENSAJE', {
      id: mensajeId, sesion_id: sesionId, rol, texto, origen: rol === 'user' ? 'usuario' : origen,
      proveedor: proveedor ?? null, orden: i + 1, created_at: momentoRelativo(-hace, i * 2),
    });
    (opciones ?? []).forEach((opcionId, j) => agregar('CHAT_QUICK_REPLY', {
      mensaje_id: mensajeId, idx: j + 1, opcion_id: opcionId, label: ETIQUETA_OPCION[opcionId], next_step: null,
    }));
  });
}

// ---------------------------------------------------------------------------
// Dispositivos y bitácora de push
// ---------------------------------------------------------------------------
const dispositivos = new Map();
for (const cliente of clientes) {
  if (!cliente.conDispositivo) continue;
  const id = `disp-${slug(cliente.nombre)}-${slug(cliente.apellido)}`;
  agregar('DISPOSITIVO', {
    id, cliente_id: cliente.id, push_token: `fcm-demo:${sha256(cliente.id).slice(0, 40)}`,
    platform: azar() < 0.8 ? 'android' : 'ios', activo: true, created_at: momentoRelativo(-entre(10, 200)), updated_at: momentoRelativo(-entre(1, 9)),
  });
  dispositivos.set(cliente.id, id);
}

let notifN = 0;
for (const cliente of clientes) {
  const dispositivo_id = dispositivos.get(cliente.id);
  if (!dispositivo_id) continue;
  const credito = principal(cliente);
  const envios = cliente.perfil === 'impecable' && notifN % 5 === 0 ? [entre(34, 40), entre(64, 70)] : [entre(6, 40)];
  for (const hace of envios) {
    const error = notifN % 12 === 11;
    agregar('NOTIFICACION_ENVIADA', {
      id: `notif-${credito.sufijo}-${hace}`, cliente_id: cliente.id, credito_id: credito.id, dispositivo_id, aviso_id: null, canal: 'simulado',
      tipo: COPY['push.tipo'], titulo: COPY['push.titulo'], cuerpo: render(COPY['push.cuerpo'], { nombre: cliente.nombre, dias: DIAS_AVISO_PUSH }),
      fecha_envio: momentoRelativo(-hace), corte_fecha: fechaRelativa(-hace + DIAS_AVISO_PUSH), dias_antes_corte: DIAS_AVISO_PUSH,
      estado: error ? 'ERROR' : 'SIMULADO', detalle_error: error ? 'Token de dispositivo no registrado en FCM (dato de prueba)' : null,
    });
    notifN++;
  }
}

// ---------------------------------------------------------------------------
// Dos clientes de prueba guiada, con datos fijos. Van después de todo lo aleatorio
// para no mover ni un dato de los demás clientes.
//   sofia.martinez: A1 con cuenta y nada activado → fecha, apartar y automático.
//   rene.aguilar:   reincidente (tres atrasos en 24 meses), sin cuenta de débito;
//                   la abre en el flujo y su primer ingreso no alcanza la cuota
//                   completa: al simular la fecha de una parte nace el choque.
// ---------------------------------------------------------------------------
const PRUEBA_GUIADA = [
  {
    nombre: 'Sofía', apellido: 'Martínez', perfil: 'impecable', cat: 'A1', tier: 'oro', arq: 'diligente', asesor: 1, meses: 64, color: '#1F5FA8',
    cuenta: { id: 'acc-sofia-0501', sufijo: '0501', number_full: '3007990501', product_name: 'Max Electrónico', balance: 2350.0 },
    credito: { id: 'cred-personal-sofia-0502', sufijo: '0502', cuota: 265.4, dia: 28, operacion: '3990502', saldo: 5310.0, tasa: 0.17, apertura: sumarMeses(HOY, -30) },
    atrasos: [],
    record: { racha: 21, progreso: 21, hitos: [['22 de 24', etiquetaAnio(sumarMeses(HOY, 1))], ['23 de 24', etiquetaAnio(sumarMeses(HOY, 2))]],
      sumandos: [`Pagaste ${MESES[(HOY.getMonth() + 11) % 12]} a tiempo`, 'Llevas 21 meses seguidos'] },
    avisos: [['paid', 'Pagado, y a tiempo', 12, 'record'], ['complete', 'Tu cuota ya está completa', 16, null]],
  },
  {
    nombre: 'René', apellido: 'Aguilar', perfil: 'mejorable', cat: 'B', tier: 'clasica', arq: 'olvidadizo', asesor: 4, meses: 30, color: '#B35C00',
    cuenta: null,
    credito: { id: 'cred-personal-rene-0601', sufijo: '0601', cuota: 180.0, dia: 15, operacion: '3990601', saldo: 3240.0, tasa: 0.21, apertura: sumarMeses(HOY, -26) },
    // Tres pagos tarde que cruzaron A→B en 24 meses: por encima de las dos incidencias toleradas.
    atrasos: [-125, -95, -65],
    record: { racha: 1, progreso: 9, hitos: [['Sale de tu historial', etiquetaAnio(sumarMeses(HOY, 6))], ['10 de 24', etiquetaAnio(sumarMeses(HOY, 1))]],
      sumandos: [`Pagaste ${MESES[(HOY.getMonth() + 11) % 12]} a tiempo`, 'Consultas tu récord seguido'] },
    avisos: [['confirm', 'Recibimos tu pago', 9, null], ['confirm', 'Recibimos tu abono', 68, null]],
  },
];
for (const c of PRUEBA_GUIADA) {
  const username = `${slug(c.nombre)}.${slug(c.apellido)}`;
  const id = `cust-${slug(c.nombre)}-${slug(c.apellido)}`;
  agregar('CLIENTE', {
    id, username, password_hash: sha256(`ruta:${username}:${CONTRASENA_DEMO}`), first_name: c.nombre, display_name: `${c.nombre} ${c.apellido}`,
    initials: `${c.nombre[0]}${c.apellido[0]}`.toUpperCase(), avatar_color: c.color, voice: 'tu', card_tier: c.tier, archetype: c.arq,
    first_time_at_risk: c.perfil === 'impecable', perfil_crediticio: c.perfil, categoria: c.cat, asesor_id: ASESORES[c.asesor].id,
    frecuencia_pago: null, telefono: null, municipio: 'San Salvador', departamento: 'San Salvador', fecha_alta: sumarMeses(HOY, -c.meses), activo: true,
  });
  if (c.cuenta) {
    agregar('CUENTA', {
      id: c.cuenta.id, cliente_id: id, tipo: 'debit', product_name: c.cuenta.product_name, number_masked: `····${c.cuenta.sufijo}`,
      number_full: c.cuenta.number_full, balance_available: c.cuenta.balance, balance_apartado: 0, currency: 'USD', is_primary_source: true,
      created_at: momentoRelativo(-400),
    });
  }
  const cr = c.credito;
  agregar('CREDITO', {
    id: cr.id, cliente_id: id, kind: 'personal', name: 'Crédito personal', number_masked: `····${cr.sufijo}`, currency: 'USD', apartable: true,
    credit_limit: null, available: null, used_pct: null, pay_contado: null, installment_amount: cr.cuota, current_due_day: cr.dia,
    operation_number: cr.operacion, saldo_capital: cr.saldo, tasa_anual: cr.tasa, dia_corte: cr.dia, fecha_apertura: cr.apertura, estado_pago: 'al_dia',
  });
  let n = 0;
  const tx = (fila) => agregar('TRANSACCION', { id: `tx-${cr.sufijo}-${++n}`, credito_id: cr.id, numero_producto: `····${cr.sufijo}`, frontera_contada: null, ...fila });
  [-125, -95, -65, -35].forEach((dia) => {
    const tarde = c.atrasos.includes(dia);
    tx({ fecha: fechaRelativa(dia), tipo: 'D', monto: cr.cuota, descripcion: 'Cuota mensual' });
    tx({ fecha: fechaRelativa(dia + (tarde ? 12 : 2)), tipo: 'H', monto: cr.cuota, descripcion: tarde ? 'Pago de cuota fuera de fecha' : 'Pago de cuota', frontera_contada: tarde ? 'A_B' : null });
  });
  tx({ fecha: fechaRelativa(-2), tipo: 'D', monto: cr.cuota, descripcion: 'Cuota mensual' });
  tx({ fecha: fechaRelativa(-2), tipo: 'H', monto: cr.cuota, descripcion: 'Pago de cuota' });
  agregar('RECORD_PAGO', {
    cliente_id: id, streak_months: c.record.racha, next_plus_one_label: `Tu próximo +1: ${etiquetaLarga(proximoDia(HOY, cr.dia))}`,
    progress_current: c.record.progreso, progress_total: 24, consults_note: NOTA_CONSULTAS,
  });
  c.record.hitos.forEach(([label, date_label], i) => agregar('RECORD_HITO', { cliente_id: id, idx: i + 1, label, date_label }));
  c.record.sumandos.forEach((label, i) => agregar('RECORD_SUMANDO', { id: `sum-${slug(c.nombre)}-${slug(c.apellido)}-${i + 1}`, cliente_id: id, label, orden: i + 1 }));
  c.avisos.forEach(([kind, title, hace, target], i) => agregar('AVISO', {
    id: `aviso-${slug(c.nombre)}-${slug(c.apellido)}-${i + 1}`, cliente_id: id, kind, title, body: null, time_label: null,
    date_label: etiquetaCorta(sumarDias(HOY, -hace)), read_flag: true, actionable: !!target, target, orden: i + 1, created_at: momentoRelativo(-hace),
  }));
}

// ---------------------------------------------------------------------------
// Emisión SQL
// ---------------------------------------------------------------------------
const ORDEN = [
  'PARAMETRO_APP', 'PARAMETRO_CORTE', 'CATALOGO_FRECUENCIA', 'CATALOGO_FRECUENCIA_DIA', 'CATALOGO_PARTES',
  'CATALOGO_DIA_ASESORIA', 'CATALOGO_HORA_ASESORIA', 'CATALOGO_CHAT_OPCION', 'OFERTA', 'OFERTA_APERTURA',
  'OFERTA_APERTURA_CONDICION', 'OFERTA_APERTURA_DOCUMENTO', 'ASESOR', 'ASESORIA_TEMA', 'SUCURSAL', 'CLIENTE', 'CUENTA', 'CREDITO', 'PRODUCTO_ACTIVADO',
  'PLAN_FECHA_COBRO', 'APARTADO', 'APARTADO_CUOTA', 'AUTOPAGO', 'AVISO', 'RECORD_PAGO', 'RECORD_HITO', 'RECORD_SUMANDO',
  'SHOCK_CONTEXT', 'CITA', 'CHAT_SESION', 'CHAT_MENSAJE', 'CHAT_QUICK_REPLY', 'TRANSACCION', 'DISPOSITIVO', 'NOTIFICACION_ENVIADA',
];
const SOLO_EJECUCION = ['EVENTO_AUDITORIA', 'LLAMADA_VOZ', 'PERFIL_INGRESO', 'IA_LLAMADA', 'SESION_TOKEN'];

function literal(v, dialecto) {
  if (v === null || v === undefined) return 'NULL';
  if (typeof v === 'object' && v.expr) return v.expr[dialecto];
  if (v instanceof Date) return `DATE '${iso(v)}'`;
  if (typeof v === 'boolean') return v ? '1' : '0';
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : v.toFixed(2);
  const texto = String(v);
  if (texto.includes(';')) throw new Error(`Texto con ';' (rompe el separador de sentencias): ${texto}`);
  return `'${texto.replace(/'/g, "''")}'`;
}

function sentencias(dialecto) {
  const out = [];
  for (const tabla of ORDEN) {
    const lista = filas.get(tabla) ?? [];
    out.push('', `-- ${tabla} (${lista.length} filas)`);
    for (const fila of lista) {
      const columnas = Object.keys(fila);
      out.push(`INSERT INTO ${tabla} (${columnas.join(', ')}) VALUES (${columnas.map((c) => literal(fila[c], dialecto)).join(', ')});`);
    }
  }
  return out;
}

const cabecera = (dialecto) => [
  '-- ============================================================================',
  `-- Ruta · Bancoagrícola — DATASET DE PRUEBA (${dialecto === 'oracle' ? 'Oracle' : 'H2'})`,
  '-- ============================================================================',
  '-- GENERADO por tools/seed/generate-seed.mjs. No editar a mano: cambiar el',
  '-- generador y volver a ejecutarlo (node tools/seed/generate-seed.mjs).',
  `-- Generado el ${iso(HOY)}. Etiquetas de copy calculadas ese día; fechas del`,
  '-- ciclo de corte y del push relativas a la fecha de carga.',
  '--',
  `-- ${clientes.length} clientes: ${clientes.filter((c) => c.perfil === 'impecable').length} impecables, ${clientes.filter((c) => c.perfil === 'mejorable').length} mejorables, ${clientes.filter((c) => c.perfil === 'fatal').length} fatales.`,
  `-- Contraseña de todos los clientes de prueba: ${CONTRASENA_DEMO}`,
  '-- IA_LLAMADA y SESION_TOKEN quedan vacías: son bitácora y sesiones reales de ejecución.',
  '-- ============================================================================',
];

const oracle = [
  ...cabecera('oracle'),
  '-- Ejecutar tras oracle-schema.sql, con NLS_LANG en UTF-8 para conservar tildes:',
  '--   set NLS_LANG=AMERICAN_AMERICA.AL32UTF8',
  '--   sqlplus JULIOPALACIOS/<password>@//localhost:1521/XEPDB1 @oracle-seed.sql',
  '',
  'SET DEFINE OFF',
  'SET FEEDBACK OFF',
  'WHENEVER SQLERROR EXIT FAILURE ROLLBACK',
  '',
  '-- Idempotente: vacía los datos antes de sembrar.',
  ...[...SOLO_EJECUCION, ...[...ORDEN].reverse()].map((t) => `DELETE FROM ${t};`),
  ...sentencias('oracle'),
  '',
  'COMMIT;',
  '',
  'WHENEVER SQLERROR CONTINUE',
  'SET PAGESIZE 100 LINESIZE 120',
  'COLUMN tabla FORMAT A28',
  'PROMPT == Filas por tabla ==',
  [...ORDEN, ...SOLO_EJECUCION].map((t, i) => `${i ? 'UNION ALL ' : ''}SELECT '${t}' AS tabla, COUNT(*) AS filas FROM ${t}`).join('\n') + ';',
  'PROMPT == Clientes por perfil y categoría ==',
  'SELECT perfil_crediticio, categoria, COUNT(*) AS clientes FROM CLIENTE GROUP BY perfil_crediticio, categoria ORDER BY 1, 2;',
  `PROMPT == Candidatos al push (1 a ${DIAS_AVISO_PUSH} días del corte, con saldo) ==`,
  `SELECT COUNT(DISTINCT credito_id) AS creditos, COUNT(DISTINCT cliente_id) AS clientes, COUNT(DISTINCT CASE WHEN push_token IS NULL THEN cliente_id END) AS sin_dispositivo FROM VW_PUSH_CANDIDATOS WHERE dias_para_corte BETWEEN 1 AND ${DIAS_AVISO_PUSH};`,
  '',
];

const h2 = [...cabecera('h2'), ...sentencias('h2'), ''];

mkdirSync(dirname(SALIDAS.h2), { recursive: true });
writeFileSync(SALIDAS.oracle, oracle.join('\n'), 'utf8');
writeFileSync(SALIDAS.h2, h2.join('\n'), 'utf8');

console.log('Dataset generado:');
for (const tabla of ORDEN) console.log(`  ${tabla.padEnd(28)} ${(filas.get(tabla) ?? []).length}`);
console.log(`  -> ${SALIDAS.oracle}`);
console.log(`  -> ${SALIDAS.h2}`);
