// Respuestas de ejemplo con los datos ilustrativos de Figma. Solo para probar la interfaz
// mientras no existe el backend; la app no trae ningún dato propio.

export const cliente = { nombre: 'Edgar', iniciales: 'EG' };

export const inicioBase = {
  cliente,
  cuenta: { producto: 'Cuenta de ahorro · Max Electrónico', saldo: 1482.14, numero: '3007040110' },
  tarjeta: { disponible: 660, limite: 1000 },
  credito: { id: 'c-0452', cuota: 248.5, diaCobro: 28, cambio: null },
};

export const productos = {
  cambiarFecha: { id: 'cambiar-fecha', titulo: 'Cambiar fecha de cobro', detalle: 'Que tu cuota caiga cuando ya te pagaron', ilustracion: 'calendario' },
  deposito: { id: 'deposito-plazo', titulo: 'Depósito a plazo digital', detalle: 'Crece a tasa fija', ilustracion: 'monedas' },
};

// Interés de los días que se corre la primera cuota: saldo $9,443 al 16 % y base 365, igual que el backend
// para Edgar (hoy día 28). Los días de noviembre corren la cuota; los de octubre la adelantan.
const INTERES_POR_DIA = (9443 * 0.16) / 365;
const diasExtra = (d) => (d < 18 ? d + 3 : 0);
const interesDe = (d) => Math.round(INTERES_POR_DIA * diasExtra(d) * 100) / 100;
const costoOpcion = (d) =>
  diasExtra(d)
    ? `Con este día tu cuota se corre ${diasExtra(d)} días y lleva $${interesDe(d).toFixed(2)} de interés, una sola vez.`
    : 'Con este día no hay interés extra.';
const dia = (d, desde, nota) => ({ dia: d, desde: `Desde el ${desde}`, nota, costo: costoOpcion(d), interes: interesDe(d), diasExtra: diasExtra(d) });
export const confirmacionFecha = (d) => {
  const desde = `${d} de ${d < 18 ? 'noviembre' : 'octubre'}`;
  return {
    dia: d,
    desde: `${desde} de 2026`,
    operacion: '3242785',
    costo: diasExtra(d)
      ? `Se cobra una sola vez, con tu cuota del ${desde}, por los ${diasExtra(d)} días que se corre tu cobro. Después tu cuota vuelve a ser la de siempre.`
      : 'Este cambio no lleva interés. Tu cuota y tu plazo no cambian.',
    interes: interesDe(d),
    diasExtra: diasExtra(d),
  };
};
export const opcionesFecha = {
  'quincena-fin-de-mes': {
    hoy: 28,
    grupos: [
      { titulo: 'DESPUÉS DE TU QUINCENA DEL 15', dias: [dia(18, '18 de octubre', 'El 18 ya llevas tres días con tu quincena en la cuenta.'), dia(19, '19 de octubre', 'El 19 ya llevas cuatro días con tu quincena en la cuenta.')] },
      { titulo: 'DESPUÉS DE TU PAGO DE FIN DE MES', dias: [dia(3, '3 de noviembre', 'El 3 ya llevas tres días con tu pago de fin de mes.'), dia(4, '4 de noviembre', 'El 4 ya llevas cuatro días con tu pago de fin de mes.')] },
    ],
  },
  'fin-de-mes': {
    hoy: 28,
    grupos: [{ titulo: 'DESPUÉS DE TU PAGO DE FIN DE MES', dias: [dia(3, '3 de noviembre', 'El 3 ya llevas tres días con tu pago.'), dia(4, '4 de noviembre', 'El 4 ya llevas cuatro días con tu pago.')] }],
  },
  semanal: {
    hoy: 28,
    grupos: [{ titulo: 'DESPUÉS DE TU PAGO DE CADA VIERNES', dias: [dia(3, '3 de noviembre', 'Cae después de tu pago semanal.'), dia(4, '4 de noviembre', 'Cae después de tu pago semanal.'), dia(18, '18 de octubre', 'Cae después de tu pago semanal.')] }],
  },
  variable: {
    hoy: 28,
    grupos: [{ titulo: 'CUANDO SUELE ENTRAR MÁS DINERO', dias: [dia(18, '18 de octubre', 'Según lo que te llegó en los últimos tres meses.'), dia(3, '3 de noviembre', 'Según lo que te llegó en los últimos tres meses.')] }],
  },
};

export const creditos = [
  { id: 'c-0452', nombre: 'Crédito personal', detalle: 'N.º 0452', monto: 248.5, nota: 'cuota del mes', ilustracion: 'bolsaDinero' },
  { id: 't-4821', nombre: 'Tarjeta de crédito', detalle: 'Visa ····4821', monto: 340, nota: 'pago de contado', ilustracion: 'tarjeta' },
];

const aparta = (fecha, detalle, monto) => ({ fecha, detalle, monto, tipo: 'aparta' });
const paga = { fecha: '18 de octubre', detalle: 'Se paga tu cuota completa', monto: 248.5, tipo: 'paga' };
export const partes = {
  credito: { nombre: 'Crédito personal', cuota: 248.5, diaPago: 18 },
  sugerencia: 'Dos partes coinciden con tu quincena y tu fin de mes.',
  opciones: [
    { partes: 2, calendario: [aparta('30 de septiembre', 'Apartamos la primera parte', 124.25), aparta('15 de octubre', 'Apartamos la segunda parte', 124.25), paga] },
    { partes: 3, calendario: [aparta('25 de septiembre', 'Apartamos la primera parte', 82.84), aparta('5 de octubre', 'Apartamos la segunda parte', 82.83), aparta('15 de octubre', 'Apartamos la tercera parte', 82.83), paga] },
    { partes: 4, calendario: [aparta('24 de septiembre', 'Apartamos la primera parte', 62.13), aparta('1 de octubre', 'Apartamos la segunda parte', 62.13), aparta('8 de octubre', 'Apartamos la tercera parte', 62.12), aparta('15 de octubre', 'Apartamos la cuarta parte', 62.12), paga] },
  ],
};

export const origen = {
  cuenta: { titulo: 'Desde tu cuenta de ahorro', nombre: 'Cuenta ····0110', detalle: 'De aquí se aparta y de aquí se paga.' },
  pasos: ['El 30 y el 15 apartamos $124.25', 'El 18 pagamos tu cuota completa', 'Te avisamos cada vez, al momento'],
};

export const resumenApartado = {
  filas: [
    { etiqueta: 'Tu cobro', valor: 'Día 18 de cada mes' },
    { etiqueta: 'Apartamos', valor: '$124.25 el 30 y el 15' },
    { etiqueta: 'Desde', valor: 'Ahorro ····0110' },
    { etiqueta: 'Primera cuota completa', valor: '18 de octubre' },
  ],
};

export const ofertaCuenta = {
  nombre: 'Cuenta de ahorro digital',
  condiciones: ['No es un crédito: no te cobra intereses', 'Sin costo de apertura y sin saldo mínimo', 'Recibe tu sueldo o pasa dinero gratis desde otro banco'],
};

export const contratoCuenta = {
  filas: [
    { etiqueta: 'Titular', valor: 'Edgar Gómez' },
    { etiqueta: 'Cuenta', valor: 'Ahorro digital' },
    { etiqueta: 'Costo de apertura', valor: '$0.00' },
    { etiqueta: 'Costo mensual', valor: '$0.00' },
    { etiqueta: 'Para qué', valor: 'Apartar tu cuota' },
  ],
  documentos: [
    { titulo: 'Contrato de cuenta de ahorro', url: 'https://www.bancoagricola.com' },
    { titulo: 'Autorización para apartar en automático', url: 'https://www.bancoagricola.com' },
  ],
};

export const record = {
  meses: 19,
  proximo: '18 de noviembre',
  hitos: [
    { n: 22, etiqueta: 'Hoy', hoy: true },
    { n: 23, etiqueta: 'Feb 2027', hoy: false },
    { n: 24, etiqueta: 'Mar 2027', hoy: false },
  ],
  explicacion: 'Los dos meses de 2025 que no fueron a tiempo salen de tu récord en febrero y marzo de 2027. Mientras, cada cuota a tiempo suma.',
  suma: [
    { titulo: 'Apartas tu cuota antes de la fecha', detalle: 'Tu cuota de octubre quedó completa 3 días antes del 18. El dinero se congela en tu cuenta y se paga entero ese día.' },
    { titulo: 'Usas el 34 % de tu línea', detalle: 'Por debajo del 50 %: tienes espacio y lo cuidas.' },
  ],
};

export function avisos() {
  const hoy = new Date();
  hoy.setHours(8, 5, 0, 0);
  return [
    { id: 'a-distinto', titulo: 'Este mes vino distinto, y está bien', cuerpo: 'No alcanzó para apartar los $124.25 de tu quincena. Toca aquí y tu asesor te ayuda a ajustarlo. Sin costo.', fecha: hoy.toISOString(), destino: { vista: 'asesor', aviso: 'a-distinto' } },
    { id: 'a-mitad', titulo: 'Ya va la mitad', cuerpo: 'Apartamos $124.25 de tu pago. Tu cuota del 18 de noviembre va a la mitad.', fecha: '2026-10-30T12:00:00.000Z', destino: null },
    { id: 'a-pagado', titulo: 'Pagado, y a tiempo', cuerpo: 'Tu cuota de $248.50 se pagó sola. Ya son 19 meses seguidos: mira tu récord.', fecha: '2026-10-18T12:00:00.000Z', destino: { vista: 'record' } },
    { id: 'a-completa', titulo: 'Tu cuota ya está completa', cuerpo: 'Apartamos los últimos $124.25. El 18 se paga sola; tú no tienes que hacer nada.', fecha: '2026-10-15T12:00:00.000Z', destino: null },
  ];
}

export const asesor = {
  saludo: { mensajes: ['Hola, Edgar. Soy tu asesor. ¿En qué te ayudo hoy?'], sugerencias: ['Mi crédito personal', 'Mi fecha de cobro', 'Apartar mi cuota'] },
  desdeAviso: {
    mensajes: ['Hola, Edgar. Vi que este mes no alcanzó para apartar los $124.25 de tu quincena. Le pasa a cualquiera, y tiene arreglo.', '¿Lo ajustamos juntos? Puedo mover tu fecha o repartir lo que falta.'],
    sugerencias: ['Mover la fecha', 'Repartir lo que falta'],
  },
  respuesta: {
    mensajes: ['Sí. Tu cobro es el día 18. Si te pagan después, lo movemos a un día en que ya tengas el dinero, sin costo.', '¿Te muestro los días que te quedan mejor?'],
    sugerencias: ['Sí, muéstrame', 'Tengo otra pregunta'],
  },
};
